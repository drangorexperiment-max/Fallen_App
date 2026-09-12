package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPreviewTest {
    @Test
    fun previewStartsAtStartNodeAndDoesNotMoveTheAuthoringCanvasSelectionOrRevision() {
        val authoring = fixture()
        val project = authoring.toProjectSnapshot()
        val preview = authoring.withPreviewStarted()

        assertTrue(preview.isPreview)
        assertEquals("home", preview.previewNodeId)
        assertEquals(authoring.nodes[0], preview.previewNode)
        assertTrue(preview.previewHistory.isEmpty())
        assertFalse(preview.canPreviewBack)
        assertEquals("details", preview.activeNodeId)
        assertEquals(authoring.activeNode, preview.activeNode)
        assertEquals(authoring.canvas, preview.canvas)
        assertSame(authoring.elements, preview.elements)
        assertEquals(authoring.selectedId, preview.selectedId)
        assertEquals(authoring.revision, preview.revision)
        assertEquals(authoring.savedRevision, preview.savedRevision)
        assertFalse(preview.isDirty)
        assertEquals(project, preview.toProjectSnapshot())
        assertSame(preview, preview.withPreviewStarted())
    }

    @Test
    fun renderProjectionShowsPreviewContentButEvenItsExportSnapshotStaysAuthoring() {
        val authoring = fixture()
        assertSame(authoring, authoring.forCanvas())
        val preview = authoring.withPreviewStarted()
        val canvas = preview.forCanvas()

        assertEquals(authoring.nodes[0].canvas, canvas.canvas)
        assertSame(authoring.nodes[0].elements, canvas.elements)
        assertNull(canvas.selectedId)
        assertNull(canvas.selectedElement)
        assertEquals("details", canvas.activeNodeId)
        assertEquals("home", canvas.previewNodeId)
        assertSame(authoring.nodes, canvas.nodes)
        assertSame(authoring.transitions, canvas.transitions)
        assertSame(authoring.assets, canvas.assets)
        assertSame(authoring.fonts, canvas.fonts)
        assertEquals(authoring.toProjectSnapshot(), canvas.toProjectSnapshot())
        assertEquals(authoring.selectedId, preview.selectedId)
        assertEquals(authoring.canvas, preview.canvas)
        assertEquals(authoring.toProjectSnapshot(), preview.toProjectSnapshot())
    }

    @Test
    fun openScreenPushesHistoryAndBackPopsItUsingPreviewSourceNotActiveNode() {
        val authoring = fixture()
        val home = authoring.withPreviewStarted()
        // "button" also exists in authoring/details, where its action is BACK, not OPEN_SCREEN.
        val details = home.withPreviewElementActivated("button")
        assertEquals("details", details.previewNodeId)
        assertEquals(listOf("home"), details.previewHistory)
        assertTrue(details.canPreviewBack)

        val end = details.withPreviewElementActivated("next")
        assertEquals("end", end.previewNodeId)
        assertEquals(listOf("home", "details"), end.previewHistory)
        val returned = end.withPreviewElementActivated("back")
        assertEquals("details", returned.previewNodeId)
        assertEquals(listOf("home"), returned.previewHistory)
        val root = returned.withPreviewElementActivated("button")
        assertEquals("home", root.previewNodeId)
        assertTrue(root.previewHistory.isEmpty())
        assertFalse(root.canPreviewBack)
        assertSame(root, root.withPreviewBack())
        assertEquals(authoring.toProjectSnapshot(), root.toProjectSnapshot())
        assertEquals(authoring.revision, root.revision)
        assertEquals(authoring.savedRevision, root.savedRevision)
    }

    @Test
    fun explicitPreviewBackMatchesBackActionAndDoesNothingOutsidePreview() {
        val authoring = fixture()
        val details = authoring.withPreviewStarted().withPreviewElementActivated("button")
        assertEquals(details.withPreviewElementActivated("button"), details.withPreviewBack())
        assertSame(authoring, authoring.withPreviewBack())
        assertSame(authoring, authoring.withPreviewElementActivated("button"))
        assertSame(authoring, authoring.withPreviewStopped())
    }

    @Test
    fun cyclesAndSelfLinksAreAllowedAndHistoryIsBounded() {
        val authoring = fixture()
        var loop = authoring.withPreviewStarted()
        repeat(PREVIEW_HISTORY_LIMIT * 3) { loop = loop.withPreviewElementActivated("self") }

        assertEquals("home", loop.previewNodeId)
        assertEquals(PREVIEW_HISTORY_LIMIT, loop.previewHistory.size)
        assertTrue(loop.previewHistory.all { it == "home" })
        assertTrue(loop.canPreviewBack)
        repeat(PREVIEW_HISTORY_LIMIT) { loop = loop.withPreviewBack() }
        assertTrue(loop.previewHistory.isEmpty())
        assertSame(loop, loop.withPreviewBack())

        repeat(PREVIEW_HISTORY_LIMIT * 2) {
            loop = loop.withPreviewElementActivated("button").withPreviewElementActivated("next")
                .withPreviewElementActivated("cycle")
        }
        assertEquals("home", loop.previewNodeId)
        assertEquals(PREVIEW_HISTORY_LIMIT, loop.previewHistory.size)
        assertEquals(authoring.toProjectSnapshot(), loop.toProjectSnapshot())
        assertEquals(authoring.revision, loop.revision)
    }

    @Test
    fun nonInteractiveMissingAndDanglingActionsCannotChangePreview() {
        val preview = fixture().withPreviewStarted()
        assertSame(preview, preview.withPreviewElementActivated("unknown"))
        assertSame(preview, preview.withPreviewElementActivated("plain"))
        assertSame(preview, preview.withPreviewElementActivated("root-back"))
        assertSame(preview, preview.withPreviewElementActivated("next")) // belongs to details

        val dangling = preview.copy(transitions = listOf(
            NodeTransition("broken", "home", "button", toNodeId = "deleted-node"),
        ))
        assertSame(dangling, dangling.withPreviewElementActivated("button"))
    }

    @Test
    fun stopRestoresExactlyTheWorkingContextAndDoesNotAddDirtyOrUndoState() {
        val authoring = fixture().copy(
            activePanel = EditorPanel.NODES,
            canUndo = true,
            canRedo = true,
            toast = "unchanged",
            saveError = "unchanged error",
        ).withProjectEdit()
        val stopped = authoring.withPreviewStarted().withPreviewElementActivated("button")
            .withPreviewElementActivated("next").withPreviewStopped()

        assertFalse(stopped.isPreview)
        assertNull(stopped.previewNode)
        assertFalse(stopped.canPreviewBack)
        assertTrue(stopped.previewHistory.isEmpty())
        assertEquals(authoring, stopped.copy(authoringEpoch = authoring.authoringEpoch))
        assertEquals(authoring.toProjectSnapshot(), stopped.toProjectSnapshot())
        assertEquals(authoring.undoSnapshot(), stopped.undoSnapshot())
        assertEquals(authoring.revision, stopped.revision)
        assertEquals(authoring.savedRevision, stopped.savedRevision)
        assertEquals(authoring.isDirty, stopped.isDirty)
        assertEquals("button", stopped.selectedId)
        assertEquals(authoring.selectedElement, stopped.selectedElement)
        assertEquals(EditorPanel.NODES, stopped.activePanel)
    }

    @Test
    fun allPureAuthoringCommandsAndUndoAreNoOpsDuringPreview() {
        val authoring = fixture()
        val preview = authoring.withPreviewStarted()
        val attempted = listOf(
            preview.withCreatedNode("New", 400, 800),
            preview.withSelectedNode("home"),
            preview.withRenamedNode("home", "Renamed"),
            preview.withDuplicatedNode("home"),
            preview.withDeletedNode("home"),
            preview.withStartNode("details"),
            preview.withElementAction("button", TransitionKind.OPEN_SCREEN, "end"),
            preview.withElementAction("button", TransitionKind.BACK),
            preview.withClearedElementAction("button"),
            preview.withRenamedAsset("image", "Renamed"),
            preview.withDeletedAsset("image"),
            preview.withDeletedFont("font"),
            preview.restoreSnapshot(authoring.undoSnapshot()),
            preview.withProjectEdit(),
        )
        attempted.forEach { assertSame(preview, it) }
        assertFalse(preview.canEditProject)
        assertNull(preview.authoringToken())
        assertEquals(authoring.toProjectSnapshot(), preview.toProjectSnapshot())
    }

    @Test
    fun asyncImportTokensAreRejectedAfterPreviewEvenIfItStoppedBeforeTheCallbackArrives() {
        val authoring = fixture()
        val token = authoring.authoringToken()!!
        assertTrue(authoring.acceptsAuthoringResult(token))
        val preview = authoring.withPreviewStarted()
        assertFalse(preview.acceptsAuthoringResult(token))
        val stopped = preview.withPreviewStopped()

        assertEquals(authoring.sessionId, stopped.sessionId)
        assertNotEquals(authoring.authoringEpoch, stopped.authoringEpoch)
        assertFalse(stopped.acceptsAuthoringResult(token))
        assertTrue(stopped.acceptsAuthoringResult(stopped.authoringToken()!!))
        assertEquals(authoring.toProjectSnapshot(), stopped.toProjectSnapshot())
    }

    @Test
    fun asyncImportTokensCannotLeakIntoANewSessionOrLeavingStateButAllowSameProjectEdits() {
        val authoring = fixture()
        val token = authoring.authoringToken()!!
        val edited = authoring.withSelectedNode("home").withRenamedNode("end", "New name")
        assertTrue(edited.acceptsAuthoringResult(token)) // images/fonts are project-global
        assertFalse(edited.copy(sessionId = authoring.sessionId + 1).acceptsAuthoringResult(token))
        assertFalse(edited.copy(isExitRequested = true).acceptsAuthoringResult(token))
        assertFalse(edited.copy(isSessionReady = false).acceptsAuthoringResult(token))
        assertNull(edited.copy(isExitRequested = true).authoringToken())
    }

    @Test
    fun previewSuspendsAutosaveAndStoppingRearmsItWithoutChangingTheRevision() {
        val dirty = fixture().withProjectEdit()
        val oldSignal = dirty.autosaveToken(enabled = true)!!
        val preview = dirty.withPreviewStarted()
        assertNull(preview.autosaveToken(enabled = true))
        val stopped = preview.withPreviewStopped()
        val newSignal = stopped.autosaveToken(enabled = true)!!

        assertEquals(oldSignal.sessionId, newSignal.sessionId)
        assertEquals(oldSignal.revision, newSignal.revision)
        // Different even if a very fast start/stop was conflated by StateFlow.
        assertNotEquals(oldSignal.authoringEpoch, newSignal.authoringEpoch)
        assertEquals(stopped.saveToken(), newSignal)
        assertTrue(stopped.isDirty)
        assertEquals(dirty.savedRevision, stopped.savedRevision)
    }

    @Test
    fun pendingOrRunningSaveMustFinishBeforePreviewStarts() {
        val normal = fixture()
        val blocked = listOf(
            normal.copy(isSaving = true),
            normal.copy(isManualSavePending = true),
            normal.copy(showSaveDialog = true),
            normal.copy(showExitDialog = true),
            normal.copy(isExitRequested = true),
            normal.copy(isSessionReady = false),
            normal.copy(isLoading = true),
        )
        blocked.forEach { assertSame(it, it.withPreviewStarted()) }
        assertTrue(normal.withPreviewStarted().isPreview)
    }

    @Test
    fun backSkipsInvalidHistoricalIdsDefensivelyWithoutTouchingAuthoring() {
        val authoring = fixture()
        val preview = authoring.withPreviewStarted().copy(
            previewNodeId = "end",
            previewHistory = listOf("home", "details", "removed"),
        )
        val returned = preview.withPreviewBack()

        assertTrue(preview.canPreviewBack)
        assertEquals("details", returned.previewNodeId)
        assertEquals(listOf("home"), returned.previewHistory)
        assertEquals(authoring.toProjectSnapshot(), returned.toProjectSnapshot())
    }

    private fun fixture(): EditorState {
        val home = ScreenNode("home", "Home", CanvasSize(1080, 1920), listOf(
            CanvasElement("button", "text", text = "Open", locked = true),
            CanvasElement("self", "text", text = "Again", z = 1),
            CanvasElement("plain", "text", text = "No action", z = 2),
            CanvasElement("root-back", "text", text = "Back", z = 3),
        ))
        val details = ScreenNode("details", "Details", CanvasSize(800, 500), listOf(
            CanvasElement("button", "text", text = "Back", fontFamily = "Brand"),
            CanvasElement("next", "image", assetId = "image", z = 1),
        ))
        val end = ScreenNode("end", "End", CanvasSize(400, 800), listOf(
            CanvasElement("back", "text", text = "Back"),
            CanvasElement("cycle", "text", text = "Home", z = 1),
        ))
        return FallenProject(
            id = "preview-project",
            name = "Preview graph",
            created = "2020-01-02T03:04:05Z",
            modified = "2021-02-03T04:05:06Z",
            nodes = listOf(home, details, end),
            startNodeId = "home",
            activeNodeId = "details",
            counter = 12,
            assets = listOf(Asset("image", "Image", "image-data")),
            fonts = listOf(ProjectFont("font", "Brand", "font-data")),
            transitions = listOf(
                NodeTransition("open", "home", "button", toNodeId = "details"),
                NodeTransition("self", "home", "self", toNodeId = "home"),
                NodeTransition("root-back", "home", "root-back", TransitionKind.BACK),
                NodeTransition("back", "details", "button", TransitionKind.BACK),
                NodeTransition("next", "details", "next", toNodeId = "end"),
                NodeTransition("end-back", "end", "back", TransitionKind.BACK),
                NodeTransition("cycle", "end", "cycle", toNodeId = "home"),
            ),
        ).toEditorState(sessionId = 9).copy(selectedId = "button")
    }
}
