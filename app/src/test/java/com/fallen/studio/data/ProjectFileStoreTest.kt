package com.fallen.studio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Только JVM/JUnit4. Валидатор намеренно тестовый: Android и ProjectSerializer не требуются. */
class ProjectFileStoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var store: ProjectFileStore

    @Before
    fun setUp() {
        directory = temporary.newFolder("projects")
        store = ProjectFileStore(directory)
    }

    @Test
    fun firstSaveRoundTripsUtf8AndLeavesNoBackupOrTemp() {
        save(FIRST)

        assertEquals(FIRST, load())
        assertEquals(FIRST, primary().readText(Charsets.UTF_8))
        assertFalse(backup().exists())
        assertFalse(pending().exists())
        assertEquals(listOf(ID), store.listIds())
        assertNoTemps()
    }

    @Test
    fun successiveSavesKeepTheImmediatelyPreviousUsableSnapshot() {
        save(FIRST)
        save(SECOND)
        assertEquals(SECOND, load())
        assertEquals(FIRST, backup().readText())

        save(THIRD)
        assertEquals(THIRD, load())
        assertEquals(SECOND, backup().readText())
        assertFalse(pending().exists())
        assertNoTemps()
    }

    @Test
    fun corruptPrimaryFallsBackWithoutChangingEitherFile() {
        seedTwoVersions()
        primary().writeText("truncated{")

        assertEquals(FIRST, load())
        assertEquals("truncated{", primary().readText())
        assertEquals(FIRST, backup().readText())
        assertEquals(listOf(ID), store.listIds())
    }

    @Test
    fun missingPrimaryCanBeOpenedAndEnumeratedFromBackupOnly() {
        seedTwoVersions()
        Files.delete(primary().toPath())

        val reopened = ProjectFileStore(directory)
        assertEquals(FIRST, load(reopened))
        assertEquals(listOf(ID), reopened.listIds())
        assertTrue(reopened.contains(ID))
        assertFalse(primary().exists())
    }

    @Test
    fun corruptPrimaryIsNeverRotatedOverGoodBackup() {
        seedTwoVersions()
        primary().writeText("corrupt")

        save(THIRD)

        assertEquals(THIRD, load())
        assertEquals(FIRST, backup().readText())
        assertFalse(pending().exists())
    }

    @Test
    fun invalidUtf8FallsBackAndIsNotRotatedOverGoodBackup() {
        seedTwoVersions()
        primary().writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        assertEquals(FIRST, load())

        save(THIRD)

        assertEquals(THIRD, load())
        assertEquals(FIRST, backup().readText())
    }

    @Test
    fun invalidUtf8WithoutBackupDoesNotHideOtherReadableProjects() {
        primary().writeBytes(byteArrayOf(0xc3.toByte()))
        store.write("healthy", SECOND, ::isValid)

        assertNull(load())
        val readableIds = store.listIds().filter { load(id = it) != null }
        assertEquals(listOf("healthy"), readableIds)
        assertTrue(primary().exists())
    }

    @Test
    fun corruptOrAbsentVersionsReturnNullWithoutInventingAProject() {
        assertNull(load())
        primary().writeText("corrupt primary")
        backup().writeText("corrupt backup")

        assertNull(load())
        assertEquals("corrupt primary", primary().readText())
        assertEquals("corrupt backup", backup().readText())
    }

    @Test
    fun pendingBackupIsPreferredAndInvalidPendingFallsThroughToStableBackup() {
        backup().writeText(FIRST)
        pending().writeText(SECOND)

        assertEquals(SECOND, load())
        assertEquals(listOf(ID), store.listIds())
        pending().writeText("truncated")
        assertEquals(FIRST, load())
    }

    @Test
    fun orphanTempsAndDirectoriesAreNeverListedOrReadAsProjects() {
        File(directory, ".fallen-interrupted.tmp").writeText(THIRD)
        File(directory, "$ID.fallen.tmp").writeText(SECOND)
        assertTrue(File(directory, "directory.fallen").mkdir())

        assertNull(load())
        assertFalse(store.contains(ID))
        assertEquals(emptyList<String>(), store.listIds())
    }

    @Test
    fun invalidNewSnapshotPreservesPrimaryAndBackup() {
        seedTwoVersions()

        assertThrows(IOException::class.java) { save("invalid") }

        assertStableVersions()
        assertNoTemps()
    }

    @Test
    fun partialTempWriteFailurePreservesBothVersions() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failWriteNumber = 1 })

        assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertNoTemps()
    }

    @Test
    fun backupTempWriteFailurePreservesBothVersions() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failWriteNumber = 2 })

        assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertFalse(pending().exists())
        assertNoTemps()
    }

    @Test
    fun pendingBackupMoveFailurePreservesBothVersions() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failMoveTarget = pending().name })

        assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertFalse(pending().exists())
        assertNoTemps()
    }

    @Test
    fun primaryMoveFailurePreservesOriginalPrimaryAndStableBackup() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failMoveTarget = primary().name })

        assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertEquals(SECOND, pending().readText())
        assertNoTemps()
        // Моделируем рестарт и повреждение primary: pending содержит старый, не новый snapshot.
        primary().writeText("corrupt")
        assertEquals(SECOND, load(ProjectFileStore(directory)))
    }

    @Test
    fun unsupportedAtomicMoveNeverFallsBackToDeleteThenRename() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { atomicMoveUnsupported = true })

        assertThrows(AtomicMoveNotSupportedException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertNoTemps()
    }

    @Test
    fun backupFinalizationFailureLeavesCommittedPrimaryAndRecoverablePending() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failMoveTarget = backup().name })

        save(THIRD, failing)

        assertEquals(THIRD, load(ProjectFileStore(directory)))
        assertEquals(FIRST, backup().readText())
        assertEquals(SECOND, pending().readText())
        primary().writeText("corrupt")
        assertEquals(SECOND, load(ProjectFileStore(directory)))
        // Ещё одна запись не должна уничтожить recovery backup повреждённым primary.
        save(THIRD)
        assertEquals(SECOND, pending().readText())
        assertEquals(FIRST, backup().readText())
    }

    @Test
    fun cleanupFailureDoesNotHideWriteFailureOrExposeOrphanTemp() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply {
            failWriteNumber = 1
            failTempDeletion = true
        })

        val error = assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertEquals("injected write failure", error.message)
        assertStableVersions()
        assertTrue(directory.listFiles()!!.any { it.extension == "tmp" })
        assertEquals(listOf(ID), ProjectFileStore(directory).listIds())
    }

    @Test
    fun primaryReadErrorCanUseBackupButNoBackupMeansAnIoError() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failReadName = primary().name })
        assertEquals(FIRST, load(failing))

        Files.delete(backup().toPath())

        assertThrows(IOException::class.java) { load(failing) }
        assertEquals(SECOND, primary().readText())
    }

    @Test
    fun unreadablePrimaryAbortsWriteRatherThanReplacingUnknownGoodData() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failReadName = primary().name })

        assertThrows(IOException::class.java) { save(THIRD, failing) }

        assertStableVersions()
        assertNoTemps()
    }

    @Test
    fun deleteRemovesPrimaryAndAllRecoveryVersionsWithoutResurrection() {
        seedTwoVersions()
        pending().writeText(SECOND)
        File(directory, ".fallen-abandoned.tmp").writeText(THIRD)

        assertTrue(store.delete(ID))

        val reopened = ProjectFileStore(directory)
        assertNull(load(reopened))
        assertFalse(primary().exists())
        assertFalse(backup().exists())
        assertFalse(pending().exists())
        assertFalse(reopened.contains(ID))
        assertEquals(emptyList<String>(), reopened.listIds())
        assertFalse(reopened.delete(ID))
    }

    @Test
    fun deleteAlsoRemovesBackupOnlyProjects() {
        backup().writeText(FIRST)
        pending().writeText(SECOND)

        assertTrue(store.delete(ID))

        assertNull(load(ProjectFileStore(directory)))
        assertEquals(emptyList<String>(), store.listIds())
    }

    @Test
    fun backupDeletionErrorIsReportedBeforePrimaryIsRemoved() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failDeleteName = backup().name })

        assertThrows(IOException::class.java) { failing.delete(ID) }

        assertStableVersions()
        assertTrue(store.delete(ID))
        assertNull(load())
    }

    @Test
    fun primaryDeletionErrorIsNotReportedAsSuccessAndLeavesPrimaryReadable() {
        seedTwoVersions()
        val failing = faultyStore(FaultIo().apply { failDeleteName = primary().name })

        assertThrows(IOException::class.java) { failing.delete(ID) }

        assertEquals(SECOND, load())
        assertEquals(listOf(ID), store.listIds())
        assertTrue(store.delete(ID))
        assertNull(load())
    }

    @Test
    fun rejectsPathTraversalSeparatorsBlankAndControlCharactersForAllIdOperations() {
        val unsafeIds = listOf("", " ", ".", "..", "../escape", "/absolute", "nested/file", "a\\b", "a\u0000b", "a\nb")
        for (id in unsafeIds) {
            assertThrows(IllegalArgumentException::class.java) { save(FIRST, id = id) }
            assertThrows(IllegalArgumentException::class.java) { load(id = id) }
            assertThrows(IllegalArgumentException::class.java) { store.delete(id) }
            assertThrows(IllegalArgumentException::class.java) { store.contains(id) }
        }
        assertFalse(File(directory.parentFile, "escape.fallen").exists())
        assertEquals(emptyList<String>(), store.listIds())
    }

    @Test
    fun existingTimestampAndOtherSafeLegacyIdsRemainReadableWithoutRenaming() {
        val ids = listOf("proj_1726145678901", "1234", "legacy-id.v3", "Проект 01", "old.fallen.bak")
        for (id in ids) {
            File(directory, "$id.fallen").writeText(FIRST)
            assertEquals(FIRST, load(id = id))
            save(SECOND, id = id)
            assertEquals(FIRST, File(directory, "$id.fallen.bak").readText())
        }
        assertEquals(ids.sorted(), store.listIds())
    }

    @Test
    fun symbolicPrimaryCannotRedirectReadsWritesOrDeletionOutsideStorage() {
        val outside = temporary.newFile("outside.fallen").apply { writeText(FIRST) }
        symlinkOrSkip(primary(), outside)

        assertThrows(IOException::class.java) { load() }
        assertThrows(IOException::class.java) { save(SECOND) }
        assertThrows(IOException::class.java) { store.delete(ID) }

        assertEquals(FIRST, outside.readText())
        assertEquals(emptyList<String>(), store.listIds())
        backup().writeText(SECOND)
        assertEquals(SECOND, load()) // Безопасный backup всё ещё можно прочитать.
    }

    @Test
    fun symbolicBackupCannotBeOverwrittenOrDeleted() {
        save(FIRST)
        val outside = temporary.newFile("outside-backup.fallen").apply { writeText(THIRD) }
        symlinkOrSkip(backup(), outside)

        assertThrows(IOException::class.java) { save(SECOND) }
        assertThrows(IOException::class.java) { store.delete(ID) }

        assertEquals(FIRST, load())
        assertEquals(THIRD, outside.readText())
    }

    @Test
    fun symbolicStorageDirectoryIsRejected() {
        val outside = temporary.newFolder("outside-directory")
        val link = File(temporary.root, "linked-projects")
        symlinkOrSkip(link, outside)

        assertThrows(IOException::class.java) { ProjectFileStore(link) }
        assertEquals(emptyList<File>(), outside.listFiles()!!.toList())
    }

    @Test
    fun fileInPlaceOfStorageDirectoryReportsIoErrorInsteadOfEmptySuccess() {
        val notDirectory = temporary.newFile("not-a-directory")
        val broken = ProjectFileStore(notDirectory)

        assertThrows(IOException::class.java) { broken.listIds() }
        assertThrows(IOException::class.java) { save(FIRST, broken) }
        assertEquals(0L, notDirectory.length())
    }

    @Test
    fun writesAndAtomicMovesUseOnlyTheStorageDirectory() {
        val observedParents = mutableListOf<File>()
        val io = object : ProjectFileStore.FileIo by ProjectFileStore.DiskFileIo {
            override fun writeSynced(file: File, raw: String) {
                observedParents += file.canonicalFile.parentFile
                ProjectFileStore.DiskFileIo.writeSynced(file, raw)
            }

            override fun atomicReplace(source: File, target: File) {
                observedParents += source.canonicalFile.parentFile
                observedParents += target.canonicalFile.parentFile
                ProjectFileStore.DiskFileIo.atomicReplace(source, target)
            }
        }
        val observed = ProjectFileStore(directory, io)

        save(FIRST, observed)
        save(SECOND, observed)

        assertTrue(observedParents.isNotEmpty())
        assertTrue(observedParents.all { it == directory.canonicalFile })
    }

    @Test(timeout = 30_000)
    fun separateInstancesAndCanonicalAliasesSerializeReentrantReadModifyWrite() {
        val workers = 4
        val iterations = 8
        val stores = List(workers) { index ->
            ProjectFileStore(if (index % 2 == 0) directory else File(directory, "."))
        }
        save("snapshot:0:end")
        val start = CountDownLatch(1)
        val inside = AtomicInteger()
        val maxInside = AtomicInteger()
        val pool = Executors.newFixedThreadPool(workers) { task ->
            Thread(task, "project-file-test").apply { isDaemon = true }
        }
        try {
            val futures = stores.map { files ->
                pool.submit {
                    start.await()
                    repeat(iterations) {
                        files.transaction {
                            val active = inside.incrementAndGet()
                            maxInside.updateAndGet { maxOf(it, active) }
                            try {
                                val value = checkNotNull(load(files))
                                    .removePrefix("snapshot:").removeSuffix(":end").toInt()
                                Thread.yield()
                                save("snapshot:${value + 1}:end", files)
                            } finally {
                                inside.decrementAndGet()
                            }
                        }
                    }
                }
            }
            start.countDown()
            futures.forEach { it.get(20, TimeUnit.SECONDS) }

            assertEquals(1, maxInside.get())
            assertEquals("snapshot:${workers * iterations}:end", load())
            assertEquals("snapshot:${workers * iterations - 1}:end", backup().readText())
            assertNoTemps()
        } finally {
            start.countDown()
            pool.shutdownNow()
            pool.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun save(raw: String, target: ProjectFileStore = store, id: String = ID) {
        target.write(id, raw, ::isValid)
    }

    private fun load(target: ProjectFileStore = store, id: String = ID): String? =
        target.read(id) { raw -> raw.takeIf(::isValid) }

    private fun isValid(raw: String): Boolean = raw.startsWith("snapshot:") && raw.endsWith(":end")

    private fun primary() = File(directory, "$ID.fallen")
    private fun backup() = File(directory, "$ID.fallen.bak")
    private fun pending() = File(directory, "$ID.fallen.bak.pending")
    private fun faultyStore(io: FaultIo) = ProjectFileStore(directory, io)

    private fun seedTwoVersions() {
        save(FIRST)
        save(SECOND)
    }

    private fun assertStableVersions() {
        assertEquals(SECOND, primary().readText())
        assertEquals(FIRST, backup().readText())
        assertEquals(SECOND, load(ProjectFileStore(directory)))
    }

    private fun assertNoTemps() {
        assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })
    }

    private fun symlinkOrSkip(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (error: IOException) {
            assumeNoException("Host does not allow symbolic links", error)
        } catch (error: UnsupportedOperationException) {
            assumeNoException("Host does not support symbolic links", error)
        } catch (error: SecurityException) {
            assumeNoException("Host does not allow symbolic links", error)
        }
    }

    private class FaultIo : ProjectFileStore.FileIo by ProjectFileStore.DiskFileIo {
        var failWriteNumber: Int? = null
        var failMoveTarget: String? = null
        var failReadName: String? = null
        var failDeleteName: String? = null
        var failTempDeletion = false
        var atomicMoveUnsupported = false
        private var writes = 0

        override fun read(file: File): String {
            if (file.name == failReadName) throw IOException("injected read failure")
            return ProjectFileStore.DiskFileIo.read(file)
        }

        override fun writeSynced(file: File, raw: String) {
            writes++
            if (writes == failWriteNumber) {
                file.writeText("partial")
                throw IOException("injected write failure")
            }
            ProjectFileStore.DiskFileIo.writeSynced(file, raw)
        }

        override fun atomicReplace(source: File, target: File) {
            if (atomicMoveUnsupported) {
                throw AtomicMoveNotSupportedException(source.path, target.path, "injected unsupported move")
            }
            if (target.name == failMoveTarget) throw IOException("injected move failure")
            ProjectFileStore.DiskFileIo.atomicReplace(source, target)
        }

        override fun deleteIfExists(file: File): Boolean {
            if (file.name == failDeleteName || (failTempDeletion && file.extension == "tmp")) {
                throw IOException("injected delete failure")
            }
            return ProjectFileStore.DiskFileIo.deleteIfExists(file)
        }
    }

    private companion object {
        const val ID = "proj_1726145678901"
        const val FIRST = "snapshot:1:Привет:end"
        const val SECOND = "snapshot:2:данные:end"
        const val THIRD = "snapshot:3:новые данные:end"
    }
}
