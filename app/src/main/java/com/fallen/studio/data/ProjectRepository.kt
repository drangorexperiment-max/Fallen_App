package com.fallen.studio.data

import android.content.Context
import android.net.Uri
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectSummary
import com.fallen.studio.data.model.toSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * Хранилище проектов: каждый проект — отдельный JSON-файл
 * в internal storage (filesDir/projects/<id>.fallen).
 * Это надёжнее и быстрее, чем один общий файл.
 */
class ProjectRepository(private val context: Context) {

    private val projectFiles by lazy { ProjectFileStore(File(context.filesDir, "projects")) }
    private val autosaveFiles by lazy { ProjectFileStore(context.filesDir) }

    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val summaries: StateFlow<List<ProjectSummary>> = _summaries.asStateFlow()

    suspend fun refresh(): Unit = withContext(Dispatchers.IO) {
        projectFiles.transaction { refreshSummariesLocked() }
    }

    suspend fun load(id: String): FallenProject? = withContext(Dispatchers.IO) {
        readProject(id)
    }

    /** Совместимый API: возвращает ID именно того snapshot, который удалось сохранить. */
    suspend fun save(project: FallenProject): String = checkNotNull(saveSnapshot(project).id)

    /**
     * Возвращает фактически записанный snapshot с id/created/modified. JSON, version и данные
     * проекта не мигрируются. При ошибке записи исключение получает вызывающий код.
     */
    suspend fun saveSnapshot(project: FallenProject): FallenProject {
        // Модели элементов immutable; отделяем списки от вызывающего кода до переключения на IO.
        val snapshot = project.detachedSnapshot()
        return withContext(Dispatchers.IO) {
            projectFiles.transaction {
                val saved = saveSnapshotLocked(snapshot)
                refreshAfterMutationLocked()
                saved
            }
        }
    }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        projectFiles.transaction {
            projectFiles.delete(id)
            // Даже если последующий refresh встретит чужой нечитаемый файл, удалённая запись
            // не должна остаться в этом StateFlow как будто проект ещё существует.
            _summaries.value = _summaries.value.filterNot { it.id == id }
            refreshAfterMutationLocked()
        }
    }

    suspend fun duplicate(id: String): String? = withContext(Dispatchers.IO) {
        projectFiles.transaction {
            val original = readProject(id) ?: return@transaction null
            val saved = saveSnapshotLocked(
                original.copy(id = null, name = original.name + " (копия)", created = ""),
            )
            refreshAfterMutationLocked()
            saved.id
        }
    }

    suspend fun rename(id: String, newName: String) = withContext(Dispatchers.IO) {
        projectFiles.transaction {
            val project = readProject(id) ?: return@transaction
            // Read-modify-write находится под тем же lock, что и обычные сохранения/удаления.
            val saved = saveSnapshotLocked(project.copy(name = newName))
            refreshAfterMutationLocked()
            checkNotNull(saved.id)
        }
    }

    private fun readProject(id: String): FallenProject? =
        projectFiles.read(id, ProjectSerializer::decode)?.copy(id = id)

    /** Вызывается только внутри projectFiles.transaction; никакого suspend под monitor. */
    private fun saveSnapshotLocked(project: FallenProject): FallenProject {
        val id = project.id?.takeIf { it.isNotBlank() } ?: newProjectIdLocked()
        val now = Instant.now().toString()
        val created = project.created.ifBlank {
            readProject(id)?.created?.takeIf { it.isNotBlank() } ?: now
        }
        val saved = project.copy(id = id, created = created, modified = now)
        projectFiles.write(id, ProjectSerializer.encode(saved)) { ProjectSerializer.decode(it) != null }
        return saved
    }

    private fun newProjectIdLocked(): String {
        var id: String
        do {
            id = "proj_${UUID.randomUUID()}"
        } while (projectFiles.contains(id))
        return id
    }

    private fun refreshSummariesLocked() {
        val list = projectFiles.listIds()
            .mapNotNull { readProject(it)?.toSummary() }
            .sortedByDescending { it.modified ?: it.created }
        _summaries.value = list
    }

    private fun refreshAfterMutationLocked() {
        try {
            refreshSummariesLocked()
        } catch (_: IOException) {
            // Snapshot уже committed. Ошибка чтения списка не должна изображать ошибку записи;
            // оставляем предыдущий список, явный refresh() по-прежнему сообщает ошибку вызывающему.
        } catch (_: SecurityException) {
            // Не подменяем существующие summaries пустым списком при недоступном хранилище.
        }
    }

    private fun FallenProject.detachedSnapshot(): FallenProject = copy(
        assets = assets.toList(),
        elements = elements.toList(),
        fonts = fonts.toList(),
    )

    // ---------- Импорт / экспорт файлов ----------

    /** Импорт .uiproj / .fallen / .json файла из старой HTML-программы или экспорта Fallen. */
    suspend fun importFromUri(uri: Uri): FallenProject? = withContext(Dispatchers.IO) {
        try {
            val raw = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext null
            ProjectSerializer.decode(raw)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    /** Экспорт проекта в выбранный пользователем файл (SAF). */
    suspend fun exportToUri(project: FallenProject, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Сериализация до открытия output stream: ошибка encode не обнулит целевой файл.
                writeBytesToUriOnIo(ProjectSerializer.encode(project).toByteArray(Charsets.UTF_8), uri)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }

    /** Запись произвольного текста (код расстановки и т.п.) в файл через SAF. */
    suspend fun writeTextToUri(text: String, uri: Uri): Boolean =
        withContext(Dispatchers.IO) { writeBytesToUriOnIo(text.toByteArray(Charsets.UTF_8), uri) }

    /** Запись бинарных данных (PNG/JPEG) в файл через SAF. */
    suspend fun writeBytesToUri(bytes: ByteArray, uri: Uri): Boolean =
        withContext(Dispatchers.IO) { writeBytesToUriOnIo(bytes, uri) }

    /** Только IO-контекст; null stream — ошибка, а не успешный экспорт. */
    private fun writeBytesToUriOnIo(bytes: ByteArray, uri: Uri): Boolean = try {
        val output = context.contentResolver.openOutputStream(uri)
        if (output == null) {
            false
        } else {
            output.use { it.write(bytes) }
            true
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    // ---------- Автосохранение ----------

    suspend fun saveAutosave(project: FallenProject) {
        val snapshot = project.detachedSnapshot()
        withContext(Dispatchers.IO) {
            autosaveFiles.write("autosave", ProjectSerializer.encode(snapshot)) {
                ProjectSerializer.decode(it) != null
            }
        }
    }

    suspend fun loadAutosave(): FallenProject? = withContext(Dispatchers.IO) {
        autosaveFiles.read("autosave", ProjectSerializer::decode)
    }

    suspend fun clearAutosave(): Boolean = withContext(Dispatchers.IO) {
        autosaveFiles.delete("autosave")
    }
}
