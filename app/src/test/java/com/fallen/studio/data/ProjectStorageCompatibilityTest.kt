package com.fallen.studio.data

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.data.model.ProjectGraph
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Pure JVM: реальные model/serializer/FileStore и файлы в TemporaryFolder.
 * canonicalSnapshot — та же подготовка данных, что у repository; Android/SAF здесь не тестируются.
 */
class ProjectStorageCompatibilityTest {
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
    fun storageDecodeSeparatesUnsupportedVersionFromCorruptionWithoutChangingImportContract() {
        for (version in listOf(-1, 0, ProjectGraph.VERSION + 1, Int.MAX_VALUE)) {
            // Не пытаемся валидировать новую схему по правилам v4 после чтения version.
            val raw = """{"version":$version,"nodes":"new schema","extension":{"keep":true}}"""
            assertNull(ProjectSerializer.decode(raw))
            val error = assertThrows(UnsupportedProjectVersionException::class.java) {
                ProjectSerializer.decodeForStorage(raw)
            }
            assertEquals(version, error.version)
            assertFalse(IOException::class.java.isInstance(error))
            assertThrows(UnsupportedProjectVersionException::class.java) {
                ProjectSerializer.isValidForStorage(raw)
            }
        }
        for (raw in listOf("{broken", "{}", """{"version":"5","canvas":{}}""",
            """{"version":4,"nodes":[]}""", """{"version":3,"canvas":{"w":0}}""")) {
            assertNull(ProjectSerializer.decodeForStorage(raw))
            assertFalse(ProjectSerializer.isValidForStorage(raw))
        }
        assertEquals(ProjectSerializer.decode(LEGACY), ProjectSerializer.decodeForStorage(LEGACY))
        assertTrue(ProjectSerializer.isValidForStorage(LEGACY))
    }

    @Test
    fun futurePrimaryDoesNotFallBackToLegacyBackupAndCannotBeOverwritten() {
        primary().writeText(FUTURE)
        backup().writeText(LEGACY)
        val before = directoryBytes()
        val io = TrackingIo()
        val checked = ProjectFileStore(directory, io)

        assertThrows(UnsupportedProjectVersionException::class.java) { load(checked) }
        assertEquals(listOf(primary().name), io.reads)
        assertThrows(UnsupportedProjectVersionException::class.java) {
            save(ProjectSerializer.canonicalSnapshot(FallenProject(id = ID, name = "Rename")), checked)
        }

        assertEquals(0, io.mutations)
        assertEquals(before, directoryBytes())
    }

    @Test
    fun corruptPrimaryThenFuturePendingDoesNotFallBackToOlderStableBackup() {
        primary().writeText("{corrupt primary")
        pending().writeText(FUTURE)
        backup().writeText(LEGACY)
        val before = directoryBytes()
        val io = TrackingIo()
        val checked = ProjectFileStore(directory, io)

        assertThrows(UnsupportedProjectVersionException::class.java) { load(checked) }
        assertEquals(listOf(primary().name, pending().name), io.reads)
        assertThrows(UnsupportedProjectVersionException::class.java) {
            save(ProjectSerializer.canonicalSnapshot(FallenProject(id = ID)), checked)
        }

        assertEquals(0, io.mutations)
        assertEquals(before, directoryBytes())
    }

    @Test
    fun futureStableRecoveryFileIsNotTreatedAsAnAbsentProject() {
        backup().writeText(FUTURE)
        val before = directoryBytes()

        assertEquals(listOf(ID), store.listIds())
        assertTrue(store.contains(ID))
        assertThrows(UnsupportedProjectVersionException::class.java) { load() }
        assertThrows(UnsupportedProjectVersionException::class.java) {
            save(ProjectSerializer.canonicalSnapshot(FallenProject(id = ID)))
        }
        assertEquals(before, directoryBytes())
    }

    @Test
    fun writeGuardChecksEveryCandidateBeforeStagingEvenWhenCreatedIsExplicit() {
        val replacement = ProjectSerializer.canonicalSnapshot(FallenProject(
            id = ID, name = "Already dated", created = "2024-01-01T00:00:00Z",
        ))
        val supported = ProjectSerializer.encode(replacement.copy(name = "Existing"))
        for (suffix in listOf(".fallen", ".fallen.bak.pending", ".fallen.bak")) {
            directory = temporary.newFolder()
            primary().writeText(supported)
            pending().writeText(LEGACY)
            backup().writeText(LEGACY)
            val futureFile = File(directory, "$ID$suffix").apply { writeText(FUTURE) }
            val before = directoryBytes()
            val io = TrackingIo()
            val checked = ProjectFileStore(directory, io)

            // Прямой write, без предварительного load для created: guard обязан жить в FileStore.
            assertThrows(UnsupportedProjectVersionException::class.java) { save(replacement, checked) }

            assertTrue(io.reads.contains(futureFile.name))
            assertEquals("No temp/write/move/delete for $suffix", 0, io.mutations)
            assertEquals(before, directoryBytes())
        }
    }

    @Test
    fun incomingFutureOrCorruptSnapshotCannotReachDisk() {
        primary().writeText(LEGACY)
        val before = directoryBytes()
        val io = TrackingIo()
        val checked = ProjectFileStore(directory, io)

        assertThrows(UnsupportedProjectVersionException::class.java) {
            checked.write(ID, FUTURE, ProjectSerializer::isValidForStorage)
        }
        assertThrows(IOException::class.java) {
            checked.write(ID, "{broken", ProjectSerializer::isValidForStorage)
        }
        assertEquals(0, io.mutations)
        assertEquals(before, directoryBytes())
    }

    @Test
    fun corruptPrimaryRecoversLegacyBackupAndSavingKeepsThatBackupRaw() {
        val corruptions = listOf(
            "{truncated".toByteArray(),
            """{"version":4,"nodes":[{"id":"a","name":"A"}],"activeNodeId":"missing"}""".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
        )
        for (corruption in corruptions) {
            directory = temporary.newFolder()
            store = ProjectFileStore(directory)
            primary().writeBytes(corruption)
            backup().writeText(LEGACY)
            val legacyBytes = backup().readBytes()
            val before = directoryBytes()

            val recovered = requireNotNull(load())
            assertEquals(ProjectSerializer.decode(LEGACY), recovered)
            assertEquals(ProjectGraph.VERSION, recovered.version)
            assertEquals(before, directoryBytes())
            val saved = recovered.copy(id = ID, name = "Recovered", modified = "now")
            save(saved)

            assertEquals(saved, load())
            assertArrayEquals(legacyBytes, backup().readBytes())
            assertFalse(pending().exists())
            assertNoTemps()
        }
    }

    @Test
    fun validLegacyPrimaryIsRotatedAsExactRawBytesRatherThanReserializedV4() {
        primary().writeText(LEGACY)
        val legacyBytes = primary().readBytes()
        val saved = requireNotNull(load()).copy(id = ID, name = "Migrated", modified = "now")

        save(saved)

        assertArrayEquals(legacyBytes, backup().readBytes())
        assertEquals(saved, load())
        assertEquals(ProjectGraph.VERSION, requireNotNull(ProjectSerializer.decodeForStorage(primary().readText())).version)
        assertFalse(pending().exists())
        assertNoTemps()
    }

    @Test
    fun preparedNewLegacyAndStaleV4SnapshotsExactlyMatchTheirDiskRoundTrip() {
        val projects = listOf(
            FallenProject(name = "New"),
            FallenProject(version = 3, canvas = CanvasSize(320, 240),
                elements = listOf(CanvasElement("el9", "text", text = "Legacy")), counter = -1),
            FallenProject(
                canvas = CanvasSize(1, 1), elements = listOf(CanvasElement("el999", "text")),
                nodes = listOf(
                    ScreenNode("a", "A", elements = listOf(CanvasElement("el7", "text"))),
                    ScreenNode("b", "B", CanvasSize(800, 600), listOf(CanvasElement("el42", "image"))),
                ),
                activeNodeId = "b", counter = 1,
                transitions = listOf(NodeTransition("open", "a", "el7", toNodeId = "b")),
            ),
        )
        projects.forEachIndexed { index, project ->
            val canonical = ProjectSerializer.canonicalSnapshot(project)
            assertEquals(ProjectGraph.normalize(project), canonical)
            // Порядок repository: detached canonical data -> metadata -> encode/write.
            val saved = canonical.copy(id = "snapshot_$index", created = "created", modified = "modified")
            val raw = ProjectSerializer.encode(saved)
            store.write(requireNotNull(saved.id), raw, ProjectSerializer::isValidForStorage)

            assertEquals(saved, store.read(saved.id, ProjectSerializer::decodeForStorage))
            assertEquals(raw, File(directory, "${saved.id}.fallen").readText())
            assertEquals(saved, ProjectGraph.normalize(saved))
            assertEquals(saved.nodes.first { it.id == saved.activeNodeId }.elements, saved.elements)
        }
        assertNoTemps()
    }

    @Test
    fun canonicalSnapshotDetachesNodesNestedElementsTransitionsAndLegacyLists() {
        val assets = mutableListOf(Asset("a", "A", "data:a"), Asset("b", "B", "data:b"))
        val fonts = mutableListOf(ProjectFont("f", "F", "data:f"), ProjectFont("g", "G", "data:g"))
        val mirror = mutableListOf(CanvasElement("el500", "text"), CanvasElement("el501", "text"))
        val firstElements = mutableListOf(CanvasElement("el7", "text"), CanvasElement("el9", "image"))
        val activeElements = mutableListOf(CanvasElement("el42", "text"), CanvasElement("image", "image"))
        val nodes = mutableListOf(
            ScreenNode("a", "A", elements = firstElements),
            ScreenNode("b", "B", CanvasSize(800, 600), activeElements),
        )
        val transitions = mutableListOf(
            NodeTransition("open", "a", "el7", toNodeId = "b"),
            NodeTransition("back", "b", "el42", TransitionKind.BACK),
        )
        val snapshot = ProjectSerializer.canonicalSnapshot(FallenProject(
            assets = assets, fonts = fonts, elements = mirror, nodes = nodes,
            transitions = transitions, activeNodeId = "b", counter = -1,
        ))
        val legacy = ProjectSerializer.canonicalSnapshot(FallenProject(
            version = 3, assets = assets, fonts = fonts, elements = mirror,
        ))
        val expected = ProjectSerializer.encode(snapshot)
        val expectedLegacy = ProjectSerializer.encode(legacy)

        assets.clear()
        fonts.clear()
        mirror.clear()
        firstElements.clear()
        activeElements.clear()
        nodes.clear()
        transitions.clear()

        assertEquals(expected, ProjectSerializer.encode(snapshot))
        assertEquals(expectedLegacy, ProjectSerializer.encode(legacy))
        assertEquals(42, snapshot.counter)
        assertEquals(501, legacy.counter)
        assertEquals(2, snapshot.nodes.size)
        assertEquals(2, snapshot.transitions.size)
        assertEquals(snapshot.nodes[1].elements, snapshot.elements)
        store.write(ID, expected, ProjectSerializer::isValidForStorage)
        assertEquals(snapshot, load())
    }

    @Test
    fun ioReadFailureDoesNotMaskFuturePendingAsRecoverableCorruption() {
        primary().writeText(LEGACY)
        pending().writeText(FUTURE)
        backup().writeText(LEGACY)
        val before = directoryBytes()
        val io = object : ProjectFileStore.FileIo by ProjectFileStore.DiskFileIo {
            override fun read(file: File): String {
                if (file == primary()) throw IOException("injected read failure")
                return ProjectFileStore.DiskFileIo.read(file)
            }
        }

        assertThrows(UnsupportedProjectVersionException::class.java) { load(ProjectFileStore(directory, io)) }
        assertEquals(before, directoryBytes())
    }

    private fun save(project: FallenProject, target: ProjectFileStore = store) =
        target.write(ID, ProjectSerializer.encode(project), ProjectSerializer::isValidForStorage)

    private fun load(target: ProjectFileStore = store): FallenProject? =
        target.read(ID, ProjectSerializer::decodeForStorage)

    private fun primary() = File(directory, "$ID.fallen")
    private fun pending() = File(directory, "$ID.fallen.bak.pending")
    private fun backup() = File(directory, "$ID.fallen.bak")

    private fun directoryBytes(): Map<String, List<Byte>> =
        directory.listFiles()!!.associate { it.name to it.readBytes().toList() }

    private fun assertNoTemps() = assertTrue(directory.listFiles()!!.none { it.extension == "tmp" })

    private class TrackingIo : ProjectFileStore.FileIo by ProjectFileStore.DiskFileIo {
        val reads = mutableListOf<String>()
        var mutations = 0

        override fun read(file: File): String {
            reads += file.name
            return ProjectFileStore.DiskFileIo.read(file)
        }

        override fun writeSynced(file: File, raw: String) {
            mutations++
            ProjectFileStore.DiskFileIo.writeSynced(file, raw)
        }

        override fun atomicReplace(source: File, target: File) {
            mutations++
            ProjectFileStore.DiskFileIo.atomicReplace(source, target)
        }

        override fun deleteIfExists(file: File): Boolean {
            mutations++
            return ProjectFileStore.DiskFileIo.deleteIfExists(file)
        }
    }

    private companion object {
        const val ID = "compat"
        const val FUTURE = """{"version":5,"nodes":"new schema","futurePayload":{"mustSurvive":true}}"""
        const val LEGACY = "{ \"version\":3, \"name\":\"Старый проект\", \"created\":\"original\", \"canvas\":{\"w\":320,\"h\":240},\n \"elements\":[{\"id\":\"el9\",\"type\":\"text\",\"text\":\"Keep\"}], \"extension\":{\"keepRaw\":true} }\r\n"
    }
}
