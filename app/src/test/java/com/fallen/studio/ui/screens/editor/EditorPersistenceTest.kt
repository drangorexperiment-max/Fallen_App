package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Чистые JVM-тесты: AndroidViewModel, Compose, дисковый IO и lifecycle здесь не запускаются. */
class EditorPersistenceTest {
    @Test
    fun projectStateRoundTripPreservesMetadataAndContent() {
        val project = fixture()

        val state = project.toEditorState(sessionId = 10)
        val snapshot = state.toProjectSnapshot()

        assertEquals(project.copy(elements = project.elements.sortedBy { it.z }), snapshot)
        assertEquals(2, state.version)
        assertEquals("2020-01-02T03:04:05Z", state.created)
        assertEquals("2021-02-03T04:05:06Z", state.modified)
        assertTrue(state.isSessionReady)
        assertFalse(state.isDirty)
    }

    @Test
    fun absentDatesAreNotInventedByEditorSnapshot() {
        val project = fixture().copy(created = "", modified = null)

        val snapshot = project.toEditorState(sessionId = 1).toProjectSnapshot()

        assertEquals("", snapshot.created)
        assertNull(snapshot.modified)
        assertEquals(project.version, snapshot.version)
    }

    @Test
    fun matchingSaveAcknowledgesRevisionWithoutResettingUiOrHistory() {
        val state = fixture().toEditorState(sessionId = 1)
            .withProjectEdit()
            .copy(selectedId = "el2", activePanel = EditorPanel.LAYERS, canUndo = true, canRedo = true)
        val token = state.saveToken()
        val saved = state.toProjectSnapshot().copy(modified = "2026-09-12T12:00:00Z")

        val result = reconcileSavedProject(state, token, saved)

        assertFalse(result.isDirty)
        assertEquals(state.revision, result.savedRevision)
        assertEquals(saved.modified, result.modified)
        assertEquals(saved.created, result.created)
        assertEquals(saved.version, result.version)
        assertEquals("el2", result.selectedId)
        assertEquals(EditorPanel.LAYERS, result.activePanel)
        assertTrue(result.canUndo)
        assertTrue(result.canRedo)
        assertSame(state.elements, result.elements)
    }

    @Test
    fun olderSnapshotCannotClearDirtyOrOverwriteNewerProjectEdits() {
        val saving = fixture().toEditorState(sessionId = 1).withProjectEdit()
        val token = saving.saveToken()
        val saved = saving.toProjectSnapshot().copy(modified = "2026-09-12T12:00:00Z")
        val newer = saving.copy(
            projectName = "Новое имя",
            canvas = CanvasSize(800, 600),
            assets = saving.assets + Asset("a2", "Second", "data:image/png;base64,new"),
            elements = saving.elements + CanvasElement("el3", "image", z = 2),
            fonts = saving.fonts + ProjectFont("f2", "Second font", "data:font/ttf;base64,new"),
            counter = 3,
            canUndo = true,
        ).withProjectEdit()

        val result = reconcileSavedProject(newer, token, saved)

        assertTrue(result.isDirty)
        assertEquals(newer.revision, result.revision)
        assertEquals(token.revision, result.savedRevision)
        assertEquals(newer.projectName, result.projectName)
        assertEquals(newer.canvas, result.canvas)
        assertSame(newer.assets, result.assets)
        assertSame(newer.elements, result.elements)
        assertSame(newer.fonts, result.fonts)
        assertEquals(3, result.counter)
        assertTrue(result.canUndo)
        assertEquals(saved.modified, result.modified)
        assertEquals(saved.created, result.created)
    }

    @Test
    fun firstSaveTransfersAssignedIdAndDatesEvenWhenThereAreNewEdits() {
        val saving = fixture().copy(id = null, created = "", modified = null)
            .toEditorState(sessionId = 7).withProjectEdit()
        val token = saving.saveToken()
        val saved = saving.toProjectSnapshot().copy(
            id = "assigned-by-repository",
            created = "2026-09-12T12:00:00Z",
            modified = "2026-09-12T12:00:01Z",
        )
        val newer = saving.copy(projectName = "Имя после начала IO", counter = 99).withProjectEdit()

        val result = reconcileSavedProject(newer, token, saved)
        val nextSnapshot = result.toProjectSnapshot()

        assertTrue(result.isDirty)
        assertEquals("assigned-by-repository", result.projectId)
        assertEquals(saved.id, nextSnapshot.id)
        assertEquals(saved.version, nextSnapshot.version)
        assertEquals(saved.created, nextSnapshot.created)
        assertEquals(saved.modified, nextSnapshot.modified)
        assertEquals("Имя после начала IO", nextSnapshot.name)
        assertEquals(99, nextSnapshot.counter)
        assertEquals(newer.revision, result.revision)
        assertEquals(token.revision, result.savedRevision)
    }

    @Test
    fun savingUntouchedNewProjectStillTransfersIdAfterFirstEdit() {
        val untouched = FallenProject(name = "Новый проект").toEditorState(sessionId = 7)
        val saved = untouched.toProjectSnapshot().copy(
            id = "first-save-at-revision-zero",
            created = "2026-09-12T12:00:00Z",
            modified = "2026-09-12T12:00:01Z",
        )
        val edited = untouched.copy(canvas = CanvasSize(600, 400)).withProjectEdit()

        val result = reconcileSavedProject(edited, untouched.saveToken(), saved)

        assertTrue(result.isDirty)
        assertEquals(1L, result.revision)
        assertEquals(0L, result.savedRevision)
        assertEquals(saved.id, result.toProjectSnapshot().id)
        assertEquals(saved.created, result.created)
        assertEquals(CanvasSize(600, 400), result.canvas)
    }

    @Test
    fun followUpSaveUsesFirstIdAndAcknowledgesOnlyItsOwnRevision() {
        val first = FallenProject(name = "Новый проект").toEditorState(sessionId = 7).withProjectEdit()
        val firstResult = first.toProjectSnapshot().copy(
            id = "one-project",
            created = "2026-09-12T12:00:00Z",
            modified = "2026-09-12T12:00:01Z",
        )
        val editedDuringFirstSave = first.copy(canvas = CanvasSize(700, 500)).withProjectEdit()
        val afterFirstSave = reconcileSavedProject(editedDuringFirstSave, first.saveToken(), firstResult)
        val secondSnapshot = afterFirstSave.toProjectSnapshot()
        val secondResult = secondSnapshot.copy(modified = "2026-09-12T12:00:02Z")

        val afterSecondSave = reconcileSavedProject(afterFirstSave, afterFirstSave.saveToken(), secondResult)

        assertEquals(firstResult.id, secondSnapshot.id)
        assertEquals(firstResult.created, secondSnapshot.created)
        assertEquals(CanvasSize(700, 500), secondSnapshot.canvas)
        assertFalse(afterSecondSave.isDirty)
        assertEquals(editedDuringFirstSave.revision, afterSecondSave.savedRevision)
        assertEquals(firstResult.id, afterSecondSave.projectId)
        assertEquals(secondResult.modified, afterSecondSave.modified)
    }

    @Test
    fun resultFromAnotherSessionCannotChangeCurrentProject() {
        val old = fixture().toEditorState(sessionId = 1).withProjectEdit()
        val current = FallenProject(name = "Другой проект").toEditorState(sessionId = 2).withProjectEdit()
        val saved = old.toProjectSnapshot().copy(modified = "2026-09-12T12:00:00Z")

        val result = reconcileSavedProject(current, old.saveToken(), saved)

        assertSame(current, result)
        assertTrue(result.isDirty)
        assertNull(result.projectId)
        assertEquals("", result.created)
    }

    @Test
    fun staleResultCannotRollBackAlreadyAcknowledgedMetadata() {
        val first = fixture().toEditorState(sessionId = 1).withProjectEdit()
        val second = first.copy(projectName = "Следующая ревизия").withProjectEdit()
        val secondSaved = second.toProjectSnapshot().copy(modified = "2026-09-12T12:00:02Z")
        val acknowledged = reconcileSavedProject(second, second.saveToken(), secondSaved)
        val firstSaved = first.toProjectSnapshot().copy(modified = "2026-09-12T12:00:01Z")

        val result = reconcileSavedProject(acknowledged, first.saveToken(), firstSaved)

        assertSame(acknowledged, result)
        assertFalse(result.isDirty)
        assertEquals(secondSaved.modified, result.modified)
        assertEquals(second.revision, result.savedRevision)
    }

    @Test
    fun impossibleFutureRevisionIsIgnored() {
        val current = fixture().toEditorState(sessionId = 1).withProjectEdit()
        val future = EditorSaveToken(current.sessionId, current.revision + 1)

        val result = reconcileSavedProject(current, future, current.toProjectSnapshot())

        assertSame(current, result)
        assertTrue(result.isDirty)
        assertEquals(0L, result.savedRevision)
    }

    @Test
    fun importedCopyKeepsMetadataButStartsUnsavedWithoutOldId() {
        val original = fixture()
        val imported = original.copy(id = null).toEditorState(sessionId = 2).withProjectEdit()

        assertTrue(imported.isDirty)
        assertNull(imported.projectId)
        assertEquals(original.version, imported.version)
        assertEquals(original.created, imported.created)
        assertEquals(original.modified, imported.modified)
    }

    @Test
    fun undoLikeEditStillAdvancesRevisionAndStaysDirtyAfterOldSave() {
        val original = fixture().toEditorState(sessionId = 1)
        val changed = original.copy(canvas = CanvasSize(800, 600)).withProjectEdit()
        val savingToken = changed.saveToken()
        val undone = changed.copy(canvas = original.canvas).withProjectEdit()
        val saved = changed.toProjectSnapshot().copy(modified = "2026-09-12T12:00:00Z")

        val result = reconcileSavedProject(undone, savingToken, saved)

        assertEquals(2L, result.revision)
        assertEquals(1L, result.savedRevision)
        assertEquals(original.canvas, result.canvas)
        assertTrue(result.isDirty)
    }

    @Test
    fun uiAndErrorFlagsDoNotAcknowledgeProjectEdits() {
        val edited = fixture().toEditorState(sessionId = 1).withProjectEdit()
        val failed = edited.copy(
            saveError = "Disk full",
            isSaving = false,
            isManualSavePending = false,
            selectedId = "el1",
            activePanel = EditorPanel.PROPERTIES,
            canRedo = true,
            toast = "Message",
        )

        assertTrue(failed.isDirty)
        assertEquals(edited.saveToken(), failed.saveToken())
        assertEquals(edited.savedRevision, failed.savedRevision)
        assertEquals(edited.toProjectSnapshot(), failed.toProjectSnapshot())
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveResultWithoutIdCannotAcknowledgeRevision() {
        val edited = fixture().toEditorState(sessionId = 1).withProjectEdit()

        reconcileSavedProject(edited, edited.saveToken(), edited.toProjectSnapshot().copy(id = null))
    }

    @Test
    fun manualSaveCompletionRearmsAutosaveForUnwrittenEdits() {
        val dirty = fixture().toEditorState(sessionId = 3).withProjectEdit()
        val waiting = dirty.copy(isManualSavePending = true).withProjectEdit()

        assertNull(waiting.autosaveToken(enabled = true))
        assertEquals(waiting.saveToken(), waiting.copy(isManualSavePending = false).autosaveToken(enabled = true))
        assertTrue(waiting.isDirty)
    }

    @Test
    fun autosaveSignalIsAbsentWhenDisabledOrLeaving() {
        val dirty = fixture().toEditorState(sessionId = 1).withProjectEdit()

        assertNull(dirty.autosaveToken(enabled = false))
        assertNull(dirty.copy(showExitDialog = true).autosaveToken(enabled = true))
        assertNull(dirty.copy(isExitRequested = true).autosaveToken(enabled = true))
        assertNull(dirty.copy(isSessionReady = false).autosaveToken(enabled = true))
        assertNull(fixture().toEditorState(sessionId = 1).autosaveToken(enabled = true))
        assertEquals(dirty.saveToken(), dirty.autosaveToken(enabled = true))
    }

    private fun fixture() = FallenProject(
        version = 2,
        id = "existing-project",
        name = "Проект",
        created = "2020-01-02T03:04:05Z",
        modified = "2021-02-03T04:05:06Z",
        canvas = CanvasSize(1920, 1080),
        assets = listOf(Asset("a1", "Image", "data:image/png;base64,test")),
        elements = listOf(
            CanvasElement("el2", "image", assetId = "a1", z = 1),
            CanvasElement("el1", "text", text = "Original", z = 0),
        ),
        fonts = listOf(ProjectFont("f1", "Project font", "data:font/ttf;base64,test")),
        counter = 2,
    )
}
