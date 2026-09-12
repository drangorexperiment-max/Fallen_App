package com.fallen.studio.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction.REPORT
import java.nio.file.DirectoryIteratorException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap

/**
 * Файловая часть хранилища, без Android и сериализатора.
 *
 * Все экземпляры для одной canonical-папки используют один reentrant monitor. Транзакции
 * синхронные: внутри них нельзя приостанавливать coroutine или брать lock другого хранилища.
 * Это блокировка внутри процесса, а не межпроцессный файловый lock.
 *
 * Новый файл сначала записывается и fsync-ится в той же папке. При наличии пригодного primary
 * его точная копия становится .bak.pending, затем temp атомарно заменяет primary (commit).
 * После commit .bak.pending заменяет .bak. До commit старые primary и .bak не затрагиваются;
 * при сбое финализации .bak.pending остаётся пригодной предыдущей версией. Чтение проверяет
 * primary, затем .bak.pending, затем .bak; никогда не восстанавливает из незавершённого temp.
 */
internal class ProjectFileStore(
    directory: File,
    private val fileIo: FileIo = DiskFileIo,
) {
    private val root = directory.canonicalFile
    private val lock = locks.getOrPut(root.path) { Any() }

    init {
        if (Files.isSymbolicLink(directory.toPath())) {
            throw IOException("Project storage must not be a symbolic link")
        }
    }

    /** Только синхронная работа; repository вызывает её на Dispatchers.IO. */
    fun <T> transaction(block: () -> T): T = synchronized(lock) { block() }

    /**
     * null означает отсутствие пригодной версии. decode возвращает null для corruption,
     * но исключение неподдерживаемой схемы (не IOException) немедленно прерывает fallback.
     */
    fun <T : Any> read(id: String, decode: (String) -> T?): T? = transaction {
        val paths = paths(id)
        ensureDirectory()
        var failure: IOException? = null
        for (file in listOf(paths.primary, paths.pendingBackup, paths.backup)) {
            try {
                val raw = readIfPresent(file) ?: continue
                val value = decode(raw) ?: continue
                return@transaction value
            } catch (_: CharacterCodingException) {
                // Повреждённая кодировка равнозначна невалидному документу: пробуем backup.
                // Один такой файл не должен прерывать перечисление исправных проектов.
                continue
            } catch (error: IOException) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            } catch (error: SecurityException) {
                val ioError = IOException("Cannot read ${file.name}", error)
                if (failure == null) failure = ioError else failure.addSuppressed(ioError)
            }
        }
        if (failure != null) throw failure
        null
    }

    /**
     * isValid возвращает false для corruption, но бросает не-IOException для неподдерживаемой
     * схемы (как и decode в read). Все existing candidates проверяются до создания temp:
     * даже пригодный primary не даёт права затереть backup более новой версии.
     * Ошибки до commit пробрасываются; старые primary/.bak при этом остаются на месте.
     */
    fun write(id: String, raw: String, isValid: (String) -> Boolean): Unit = transaction {
        val paths = paths(id)
        ensureDirectory()
        if (!isValid(raw)) throw IOException("Refusing an invalid project snapshot")
        val candidates = listOf(paths.primary, paths.pendingBackup, paths.backup)
        candidates.forEach { exists(it) }
        var previous: String? = null
        for (file in candidates) {
            val candidate = try {
                readIfPresent(file)
            } catch (_: CharacterCodingException) {
                // Невалидный UTF-8 — corruption; такой primary не ротируем поверх backup.
                continue
            }
            if (candidate != null) {
                val valid = isValid(candidate) // Unsupported version прерывает запись, не fallback.
                if (file == paths.primary && valid) previous = candidate
            }
        }
        val staged = stage(raw)
        var stagedBackup: File? = null
        try {
            if (previous != null) {
                val backupTemp = stage(previous)
                stagedBackup = backupTemp
                fileIo.atomicReplace(backupTemp, paths.pendingBackup)
                syncDirectory()
            }
            fileIo.atomicReplace(staged, paths.primary)
            syncDirectory()
            if (previous != null) {
                // Primary уже committed. Сбой этой оптимизации не превращает успешное сохранение
                // в ошибку: pending остаётся предыдущей версией и читается раньше старого .bak.
                try {
                    fileIo.atomicReplace(paths.pendingBackup, paths.backup)
                    syncDirectory()
                } catch (_: IOException) {
                    // Не удалять pending: это recovery backup.
                } catch (_: SecurityException) {
                    // Не удалять pending: это recovery backup.
                }
            }
        } finally {
            discardTemp(staged)
            stagedBackup?.let(::discardTemp)
        }
    }

    /** Удаляет backup до primary: прерванное удаление не может воскресить удалённый primary. */
    fun delete(id: String): Boolean = transaction {
        val paths = paths(id)
        ensureDirectory()
        listOf(paths.backup, paths.pendingBackup, paths.primary).forEach { exists(it) }
        var deleted = fileIo.deleteIfExists(paths.backup)
        deleted = fileIo.deleteIfExists(paths.pendingBackup) || deleted
        syncDirectory()
        deleted = fileIo.deleteIfExists(paths.primary) || deleted
        syncDirectory()
        deleted
    }

    /** Учитывает recovery-only проекты, но не temp и не symbolic links. */
    fun listIds(): List<String> = transaction {
        ensureDirectory()
        try {
            Files.newDirectoryStream(root.toPath()).use { entries ->
                entries.mapNotNull { path ->
                    val name = path.fileName.toString()
                    val suffix = suffixes.firstOrNull { name.endsWith(it) }
                        ?: return@mapNotNull null
                    val id = name.removeSuffix(suffix)
                    if (!validId(id)) return@mapNotNull null
                    val attributes = try {
                        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                    } catch (_: NoSuchFileException) {
                        return@mapNotNull null
                    }
                    if (attributes.isRegularFile) id else null
                }.distinct().sorted()
            }
        } catch (error: DirectoryIteratorException) {
            throw IOException("Cannot enumerate project storage: $root", error)
        }
    }

    /** Даже повреждённый primary/backup резервирует ID; UUID не должен его перезаписать. */
    fun contains(id: String): Boolean = transaction {
        val paths = paths(id)
        ensureDirectory()
        listOf(paths.primary, paths.pendingBackup, paths.backup).any { exists(it) }
    }

    private fun paths(id: String): ProjectPaths {
        require(validId(id)) { "Project ID must be a non-blank file name, not a path" }
        return ProjectPaths(
            primary = File(root, "$id.fallen"),
            backup = File(root, "$id.fallen.bak"),
            pendingBackup = File(root, "$id.fallen.bak.pending"),
        )
    }

    private fun ensureDirectory() {
        if (root.canonicalFile != root || Files.isSymbolicLink(root.toPath())) {
            throw IOException("Project storage path changed")
        }
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw IOException("Cannot create project storage: $root")
        }
    }

    private fun exists(file: File): Boolean {
        if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile.parentFile != root) {
            throw IOException("Unsafe project file path: ${file.name}")
        }
        val attributes = try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, NOFOLLOW_LINKS)
        } catch (_: NoSuchFileException) {
            return false
        }
        if (!attributes.isRegularFile) throw IOException("Not a project file: ${file.name}")
        return true
    }

    private fun readIfPresent(file: File): String? = if (exists(file)) fileIo.read(file) else null

    private fun stage(raw: String): File {
        val temp = File.createTempFile(".fallen-", ".tmp", root)
        try {
            fileIo.writeSynced(temp, raw)
            return temp
        } catch (error: Exception) {
            discardTemp(temp)
            throw error
        }
    }

    private fun discardTemp(file: File) {
        try {
            fileIo.deleteIfExists(file)
        } catch (_: IOException) {
            // Оставшийся temp не участвует ни в чтении, ни в перечислении проектов.
        } catch (_: SecurityException) {
            // Не маскировать исходную ошибку сохранения ошибкой очистки temp.
        }
    }

    private fun syncDirectory() {
        // Поддержка directory fsync зависит от JVM/файловой системы. Файлы fsync-ятся строго,
        // directory — best effort; ATOMIC_MOVE не заменяется небезопасным delete+rename.
        try {
            FileChannel.open(root.toPath(), READ).use { it.force(true) }
        } catch (_: IOException) {
        } catch (_: UnsupportedOperationException) {
        } catch (_: SecurityException) {
        }
    }

    private data class ProjectPaths(val primary: File, val backup: File, val pendingBackup: File)

    /** Узкий fault-injection seam для JVM-тестов; без Android Context и Robolectric. */
    internal interface FileIo {
        fun read(file: File): String
        fun writeSynced(file: File, raw: String)
        fun atomicReplace(source: File, target: File)
        fun deleteIfExists(file: File): Boolean
    }

    internal object DiskFileIo : FileIo {
        override fun read(file: File): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(REPORT)
            .onUnmappableCharacter(REPORT)
            .decode(ByteBuffer.wrap(Files.readAllBytes(file.toPath())))
            .toString()

        override fun writeSynced(file: File, raw: String) {
            FileOutputStream(file).use { output ->
                output.write(raw.toByteArray(Charsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
        }

        override fun atomicReplace(source: File, target: File) {
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        }

        override fun deleteIfExists(file: File): Boolean = Files.deleteIfExists(file.toPath())
    }

    private companion object {
        // Папок мало (projects + autosave); стабильный lock не удаляется из map во время работы.
        val locks = ConcurrentHashMap<String, Any>()
        val suffixes = listOf(".fallen.bak.pending", ".fallen.bak", ".fallen")

        fun validId(id: String): Boolean = id.isNotBlank() && id != "." && id != ".." &&
            id.none { it == '/' || it == '\\' || it.isISOControl() }
    }
}
