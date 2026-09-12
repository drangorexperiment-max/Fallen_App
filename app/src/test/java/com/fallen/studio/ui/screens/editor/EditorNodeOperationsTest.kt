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

/** The same pure operations are used by the VM, without Android/lifecycle or disk IO. */
class EditorNodeOperationsTest {
    @Test
    fun newProjectStartsWithExactlyOneNodeAndAnAuthoringMirror() {
        val state = FallenProject(name = "New", canvas = CanvasSize(400, 700)).toEditorState(1)
        val node = state.nodes.single()

        assertEquals(node.id, state.activeNodeId)
        assertEquals(node.id, state.startNodeId)
        assertEquals(CanvasSize(400, 700), node.canvas)
        assertEquals(node.canvas, state.canvas)
        assertTrue(node.elements.isEmpty())
        assertTrue(state.elements.isEmpty())
        assertTrue(state.transitions.isEmpty())
        assertFalse(state.isPreview)
        assertFalse(state.isDirty)
        assertEquals(state.toProjectSnapshot(), state.toProjectSnapshot().toEditorState(2).toProjectSnapshot())
    }

    @Test
    fun editingCanvasAndElementsImmediatelyUpdatesOnlyTheActiveNode() {
        val before = fixture()
        val sibling = before.nodes[1]
        val editedElements = before.elements.map { it.copy(x = it.x + 5f, showDimensions = false) }
        val edited = before.copy(canvas = CanvasSize(480, 800), elements = editedElements).withProjectEdit()

        assertEquals(1L, edited.revision)
        assertEquals(CanvasSize(480, 800), edited.activeNode!!.canvas)
        assertSame(editedElements, edited.activeNode!!.elements)
        assertSame(sibling, edited.nodes[1])
        assertEquals(before.transitions, edited.transitions)
        assertEquals(edited.nodes, edited.toProjectSnapshot().nodes)
        assertEquals(edited.elements, edited.toProjectSnapshot().elements)
    }

    @Test
    fun switchingBetweenNodesRetainsBothIndependentCanvasesAndClearsSelection() {
        val first = fixture().copy(selectedId = "hero").withProjectEdit()
        val second = first.withSelectedNode("details")
        val editedSecond = second.copy(
            canvas = CanvasSize(800, 600),
            elements = second.elements.map { it.copy(rotation = 27f) },
        ).withProjectEdit()
        val back = editedSecond.withSelectedNode("home")

        assertNull(second.selectedId)
        assertEquals(first.nodes[1].elements, second.elements)
        assertEquals(first.nodes[1].canvas, second.canvas)
        assertEquals(first.nodes[0], back.activeNode)
        assertEquals(first.elements, back.elements)
        assertEquals(CanvasSize(800, 600), back.nodes[1].canvas)
        assertEquals(editedSecond.elements, back.nodes[1].elements)
        assertEquals("home", back.startNodeId)
        assertEquals(first.revision + 3, back.revision)
        assertTrue(back.isDirty)
        assertSame(back, back.withSelectedNode("home"))
        assertSame(back, back.withSelectedNode("missing"))
    }

    @Test
    fun createValidatesNameAndSizeAndKeepsStartAndSharedResources() {
        val before = fixture().copy(selectedId = "hero")
        val created = before.withCreatedNode("  Checkout  ", 390, 844)

        assertEquals(3, created.nodes.size)
        assertEquals("Checkout", created.activeNode!!.name)
        assertEquals(CanvasSize(390, 844), created.canvas)
        assertTrue(created.elements.isEmpty())
        assertNull(created.selectedId)
        assertEquals(before.nodes, created.nodes.take(2))
        assertEquals(before.startNodeId, created.startNodeId)
        assertSame(before.assets, created.assets)
        assertSame(before.fonts, created.fonts)
        assertSame(before.transitions, created.transitions)
        assertEquals(before.counter, created.counter)
        assertEquals(before.revision + 1, created.revision)
        assertSame(before, before.withCreatedNode(" ", 400, 800))
        assertSame(before, before.withCreatedNode("Tiny", 99, 800))
        assertSame(before, before.withCreatedNode("Large", 400, 10001))
        assertSame(before, before.withCreatedNode("Negative", -1, 800))
    }

    @Test
    fun renameAndStartCanChangeInactiveNodesWithoutChangingTheWorkingCanvas() {
        val before = fixture().copy(selectedId = "hero")
        val renamed = before.withRenamedNode("details", "  Product details  ")
        val started = renamed.withStartNode("details")

        assertEquals("Product details", started.nodes[1].name)
        assertEquals(before.activeNode, started.activeNode)
        assertEquals(before.canvas, started.canvas)
        assertSame(before.elements, started.elements)
        assertEquals("hero", started.selectedId)
        assertEquals("details", started.startNodeId)
        assertEquals(2L, started.revision)
        assertSame(started, started.withRenamedNode("details", "Product details"))
        assertSame(started, started.withRenamedNode("home", " "))
        assertSame(started, started.withRenamedNode("absent", "Name"))
        assertSame(started, started.withStartNode("details"))
        assertSame(started, started.withStartNode("absent"))
    }

    @Test
    fun duplicateRemapsNodeElementsActionsAndSelfLinksWithoutLosingAnyElementFlags() {
        val before = fixture()
        val source = before.nodes[0]
        val duplicated = before.withDuplicatedNode(source.id)
        val copy = duplicated.activeNode!!

        assertNotEquals(source.id, copy.id)
        assertEquals("Home (копия)", copy.name)
        assertEquals(source.canvas, copy.canvas)
        assertEquals(source.elements.size, copy.elements.size)
        assertEquals(before.nodes, duplicated.nodes.take(2))
        assertEquals(before.startNodeId, duplicated.startNodeId)
        assertSame(before.assets, duplicated.assets)
        assertSame(before.fonts, duplicated.fonts)
        assertEquals(before.counter + source.elements.size, duplicated.counter)
        assertEquals(1L, duplicated.revision)
        assertNull(duplicated.selectedId)

        val oldIds = before.nodes.flatMap { it.elements }.map { it.id }.toSet()
        val newIds = copy.elements.map { it.id }
        assertEquals(newIds.size, newIds.toSet().size)
        assertTrue(newIds.none { it in oldIds })
        source.elements.zip(copy.elements).forEach { (old, new) ->
            assertEquals(old.copy(id = new.id), new)
        }
        val actions = duplicated.transitions.filter { it.fromNodeId == copy.id }
        assertEquals(before.transitions.count { it.fromNodeId == source.id }, actions.size)
        assertEquals(duplicated.transitions.size, duplicated.transitions.map { it.id }.toSet().size)
        val mapping = source.elements.zip(copy.elements).associate { it.first.id to it.second.id }
        before.transitions.filter { it.fromNodeId == source.id }.forEach { old ->
            val new = actions.single { it.elementId == mapping[old.elementId] }
            assertNotEquals(old.id, new.id)
            assertEquals(old.kind, new.kind)
            assertEquals(if (old.toNodeId == source.id) copy.id else old.toNodeId, new.toNodeId)
        }
        assertTrue(duplicated.transitions.containsAll(before.transitions))
        assertEquals(copy.canvas, duplicated.toProjectSnapshot().canvas)
        assertEquals(copy.elements, duplicated.toProjectSnapshot().elements)
    }

    @Test
    fun repeatedDuplicationAllocatesDistinctNamesIdsAndActionIds() {
        val once = fixture().withDuplicatedNode("home")
        val twice = once.withDuplicatedNode("home")

        assertEquals("Home (копия) 2", twice.activeNode!!.name)
        assertNotEquals(once.activeNodeId, twice.activeNodeId)
        assertEquals(twice.nodes.size, twice.nodes.map { it.id }.toSet().size)
        assertEquals(twice.transitions.size, twice.transitions.map { it.id }.toSet().size)
        assertTrue(once.activeNode!!.elements.none { old -> twice.elements.any { it.id == old.id } })
        assertSame(twice, twice.withDuplicatedNode("unknown"))
    }

    @Test
    fun deletePrunesIncomingOutgoingAndSelfEdgesAndUndoRestoresTheEntireGraph() {
        val before = fixture().withStartNode("details").withSelectedNode("details")
        val snapshot = before.undoSnapshot()
        val deleted = before.withDeletedNode("details")

        assertEquals(listOf("home"), deleted.nodes.map { it.id })
        assertEquals("home", deleted.activeNodeId)
        assertEquals("home", deleted.startNodeId)
        assertEquals(deleted.activeNode!!.canvas, deleted.canvas)
        assertEquals(deleted.activeNode!!.elements, deleted.elements)
        assertTrue(deleted.transitions.none { it.fromNodeId == "details" || it.toNodeId == "details" })
        assertEquals(setOf("home-self", "home-back"), deleted.transitions.map { it.id }.toSet())
        assertEquals(before.counter, deleted.counter)
        assertSame(before.assets, deleted.assets)
        assertSame(before.fonts, deleted.fonts)

        val restored = deleted.restoreSnapshot(snapshot)
        assertEquals(before.toProjectSnapshot(), restored.toProjectSnapshot())
        assertEquals(before.nodes, restored.nodes)
        assertEquals(before.transitions, restored.transitions)
        assertEquals("details", restored.startNodeId)
        assertEquals("details", restored.activeNodeId)
        assertEquals(deleted.revision + 1, restored.revision)
        assertTrue(restored.isDirty)
    }

    @Test
    fun lastNodeAndUnknownNodesCannotBeDeleted() {
        val only = FallenProject().toEditorState(1)
        assertSame(only, only.withDeletedNode(only.activeNodeId!!))
        assertSame(only, only.withDeletedNode("unknown"))
        val multiple = fixture()
        assertSame(multiple, multiple.withDeletedNode("unknown"))
    }

    @Test
    fun assetDeletionCascadesAcrossAllNodesAndPrunesSourcesByNodeNotJustElementId() {
        val before = fixture().copy(selectedId = "hero")
        val deleted = before.withDeletedAsset("shared")

        assertEquals(listOf("other"), deleted.assets.map { it.id })
        assertTrue(deleted.nodes.flatMap { it.elements }.none { it.assetId == "shared" })
        assertNull(deleted.selectedId)
        // Same local element ID in another node is unrelated and must survive with its action.
        assertTrue(deleted.nodes[1].elements.any { it.id == "hero" && it.assetId == "other" })
        assertTrue(deleted.transitions.any { it.id == "details-hero" })
        assertFalse(deleted.transitions.any { it.id == "home-hero" || it.id == "details-image" })
        assertEquals(before.transitions.size - 2, deleted.transitions.size)
        assertSame(before.fonts, deleted.fonts)
        assertEquals(before.counter, deleted.counter)
        assertEquals(before.toProjectSnapshot(), deleted.restoreSnapshot(before.undoSnapshot()).toProjectSnapshot())
    }

    @Test
    fun assetRenameUpdatesInstancesOnEveryNodeButLeavesOtherAssetsAndActionsAlone() {
        val before = fixture()
        val renamed = before.withRenamedAsset("shared", "  New image  ")

        assertEquals("New image", renamed.assets.single { it.id == "shared" }.name)
        renamed.nodes.flatMap { it.elements }.filter { it.assetId == "shared" }.forEach {
            assertEquals("New image", it.name)
        }
        assertEquals(before.nodes[1].elements.first(), renamed.nodes[1].elements.first())
        assertSame(before.transitions, renamed.transitions)
        assertEquals(before.canvas, renamed.canvas)
        assertSame(renamed, renamed.withRenamedAsset("shared", "New image"))
        assertSame(renamed, renamed.withRenamedAsset("shared", " "))
        assertSame(renamed, renamed.withRenamedAsset("missing", "Name"))
    }

    @Test
    fun fontRemovalResetsIdAndNameReferencesOnAllNodesAndIsFullyUndoable() {
        val before = fixture()
        val deleted = before.withDeletedFont("brand-font")

        assertTrue(deleted.fonts.isEmpty())
        assertEquals("Inter", deleted.nodes[0].elements.single { it.id == "button" }.fontFamily)
        assertEquals("Inter", deleted.nodes[1].elements.single { it.id == "button" }.fontFamily)
        assertEquals("Roboto", deleted.nodes[0].elements.single { it.id == "back" }.fontFamily)
        assertSame(before.transitions, deleted.transitions)
        assertSame(before.assets, deleted.assets)
        assertEquals(before.toProjectSnapshot(), deleted.restoreSnapshot(before.undoSnapshot()).toProjectSnapshot())
        assertSame(deleted, deleted.withDeletedFont("brand-font"))
        assertSame(deleted, deleted.withDeletedAsset("absent"))
    }

    @Test
    fun deletingAnElementOrClearingCanvasPrunesOnlyThatNodesSourceActions() {
        val before = fixture().copy(selectedId = "hero")
        val oneDeleted = before.copy(elements = before.elements.filterNot { it.id == "hero" }).withProjectEdit()
        assertNull(oneDeleted.selectedId)
        assertFalse(oneDeleted.transitions.any { it.id == "home-hero" })
        assertTrue(oneDeleted.transitions.any { it.id == "details-hero" })
        assertEquals(before.nodes[1], oneDeleted.nodes[1])

        val cleared = before.copy(elements = emptyList()).withProjectEdit()
        assertTrue(cleared.activeNode!!.elements.isEmpty())
        assertEquals(before.nodes[1], cleared.nodes[1])
        assertTrue(cleared.transitions.none { it.fromNodeId == "home" })
        assertTrue(cleared.transitions.any { it.fromNodeId == "details" && it.toNodeId == "home" })
        assertEquals(before.toProjectSnapshot(), cleared.restoreSnapshot(before.undoSnapshot()).toProjectSnapshot())
    }

    @Test
    fun elementActionsAreValidatedUniqueReplaceableAndScopedToTheActiveNode() {
        val before = fixture()
        assertSame(before, before.withElementAction("button", TransitionKind.OPEN_SCREEN, "missing"))
        assertSame(before, before.withElementAction("missing", TransitionKind.OPEN_SCREEN, "home"))
        assertSame(before, before.withElementAction("button", TransitionKind.OPEN_SCREEN, "details"))

        val back = before.withElementAction("button", TransitionKind.BACK, "ignored")
        val action = back.transitions.single { it.fromNodeId == "home" && it.elementId == "button" }
        assertEquals("home-button", action.id)
        assertEquals(TransitionKind.BACK, action.kind)
        assertNull(action.toNodeId)
        assertEquals(before.transitions.size, back.transitions.size)
        assertEquals(before.transitions.single { it.id == "details-button" },
            back.transitions.single { it.id == "details-button" })

        val self = back.withElementAction("button", TransitionKind.OPEN_SCREEN, "home")
        assertEquals("home", self.transitions.single { it.id == "home-button" }.toNodeId)
        val cleared = self.withClearedElementAction("button")
        assertFalse(cleared.transitions.any { it.id == "home-button" })
        assertTrue(cleared.transitions.any { it.id == "details-button" })
        assertSame(cleared, cleared.withClearedElementAction("button"))
        assertSame(cleared, cleared.withClearedElementAction("missing"))
    }

    @Test
    fun pruningDropsDanglingSourcesAndTargetsButKeepsValidCycles() {
        val before = fixture()
        val invalid = listOf(
            NodeTransition("no-node", "absent", "hero", toNodeId = "home"),
            NodeTransition("no-element", "home", "absent", toNodeId = "details"),
            NodeTransition("no-target", "home", "button", toNodeId = "absent"),
            NodeTransition("null-target", "home", "button", toNodeId = null),
        )
        assertEquals(before.transitions, pruneNodeTransitions(before.nodes, invalid + before.transitions))
        assertTrue(before.transitions.any { it.fromNodeId == it.toNodeId })
        assertSame(before.transitions, pruneNodeTransitions(before.nodes, before.transitions))
    }

    @Test
    fun fullUndoRedoSnapshotPreservesNewIoMetadataRevisionsAndUiFlags() {
        val initial = fixture().copy(selectedId = "hero", canUndo = true)
        val firstSnapshot = initial.undoSnapshot()
        val edited = initial.withDeletedAsset("shared").withDeletedFont("brand-font")
            .withDuplicatedNode("details").withStartNode("details")
        val redoSnapshot = edited.undoSnapshot()
        val saved = edited.toProjectSnapshot().copy(
            id = "saved-id",
            created = "authoritative-created",
            modified = "authoritative-modified",
        )
        val acknowledged = reconcileSavedProject(edited, edited.saveToken(), saved).copy(
            activePanel = EditorPanel.NODES,
            canUndo = true,
            canRedo = true,
            isSaving = true,
            isManualSavePending = true,
            showExitDialog = true,
            saveError = "keep diagnostic",
            toast = "keep toast",
        )
        val undone = acknowledged.restoreSnapshot(firstSnapshot)

        assertEquals(firstSnapshot, undone.undoSnapshot())
        assertEquals(saved.id, undone.projectId)
        assertEquals(saved.created, undone.created)
        assertEquals(saved.modified, undone.modified)
        assertEquals(saved.version, undone.version)
        assertEquals(acknowledged.revision + 1, undone.revision)
        assertEquals(acknowledged.savedRevision, undone.savedRevision)
        assertTrue(undone.isDirty)
        assertTrue(undone.canUndo)
        assertTrue(undone.canRedo)
        assertTrue(undone.isSaving)
        assertTrue(undone.isManualSavePending)
        assertTrue(undone.showExitDialog)
        assertEquals(EditorPanel.NODES, undone.activePanel)
        assertEquals("keep diagnostic", undone.saveError)
        assertEquals("keep toast", undone.toast)
        assertEquals(initial.sessionId, undone.sessionId)
        assertEquals(initial.counter, undone.counter)
        assertEquals(initial.nodes, undone.nodes)
        assertEquals(initial.transitions, undone.transitions)
        assertEquals(initial.assets, undone.assets)
        assertEquals(initial.fonts, undone.fonts)

        val redone = undone.restoreSnapshot(redoSnapshot)
        assertEquals(redoSnapshot, redone.undoSnapshot())
        assertEquals(undone.revision + 1, redone.revision)
        assertEquals(saved.id, redone.projectId)
        assertEquals(saved.modified, redone.modified)
        assertEquals(acknowledged.savedRevision, redone.savedRevision)
    }

    @Test
    fun saveRenameSurvivesElementAndNodeUndoRedoAndRemainsInProjectSnapshots() {
        val initial = fixture()
        val elementEdit = initial.copy(
            elements = initial.elements.map { it.copy(x = it.x + 10f) },
        ).withProjectEdit()
        val nodeEdit = elementEdit.withRenamedNode("details", "Changed node")
        // requestSave changes the name/revision without pushing an undo entry.
        val renamed = nodeEdit.copy(projectName = "Saved name").withProjectEdit()
        assertEquals(nodeEdit.undoSnapshot(), renamed.undoSnapshot())
        assertTrue(renamed.isDirty)
        val saved = renamed.toProjectSnapshot().copy(modified = "rename-save")
        val acknowledged = reconcileSavedProject(renamed, renamed.saveToken(), saved)
        assertEquals("Saved name", saved.name)
        assertFalse(acknowledged.isDirty)

        val undoNode = acknowledged.restoreSnapshot(elementEdit.undoSnapshot())
        val undoElement = undoNode.restoreSnapshot(initial.undoSnapshot())
        val redoElement = undoElement.restoreSnapshot(elementEdit.undoSnapshot())
        val redoNode = redoElement.restoreSnapshot(nodeEdit.undoSnapshot())

        assertEquals(elementEdit.nodes, undoNode.nodes)
        assertEquals(initial.nodes, undoElement.nodes)
        assertEquals(elementEdit.nodes, redoElement.nodes)
        assertEquals(nodeEdit.nodes, redoNode.nodes)
        listOf(undoNode, undoElement, redoElement, redoNode).forEach { result ->
            assertEquals("Saved name", result.projectName)
            assertEquals("Saved name", result.toProjectSnapshot().name)
            assertEquals(saved.id, result.projectId)
            assertEquals(saved.modified, result.modified)
            assertEquals(acknowledged.savedRevision, result.savedRevision)
            assertTrue(result.revision > acknowledged.revision)
            assertTrue(result.isDirty)
        }
    }

    @Test
    fun failedSaveRenameRemainsDirtyAndIsNotRolledBackWithAnOlderNodeEdit() {
        val initial = fixture()
        val edited = initial.withCreatedNode("New", 400, 800)
        val failed = edited.copy(projectName = "Unsaved name").withProjectEdit()
            .copy(saveError = "Disk full")
        val undone = failed.restoreSnapshot(initial.undoSnapshot())

        assertTrue(failed.isDirty)
        assertTrue(undone.isDirty)
        assertEquals("Unsaved name", undone.projectName)
        assertEquals("Unsaved name", undone.toProjectSnapshot().name)
        assertEquals(initial.nodes, undone.nodes)
        assertEquals(initial.savedRevision, undone.savedRevision)
        assertEquals(failed.revision + 1, undone.revision)
        assertEquals("Disk full", undone.saveError)
    }

    @Test
    fun newElementCounterAvoidsIdsInInactiveNodesAsWellAsTheWorkingCanvas() {
        val state = fixture()
        val extra = state.nodes[1].copy(elements = state.nodes[1].elements + CanvasElement("el1", "text"))
        val undercounted = state.copy(counter = 0, nodes = listOf(state.nodes[0], extra))
        assertEquals(2, undercounted.nextElementCounter())
    }

    @Test
    fun exhaustedElementCountersCannotWrapOrPartiallyDuplicateANode() {
        val exhausted = fixture().copy(counter = Int.MAX_VALUE - 1)
        assertNull(exhausted.nextElementCounter())
        assertSame(exhausted, exhausted.withDuplicatedNode("home"))
        val almostFull = exhausted.copy(counter = Int.MAX_VALUE - 2)
        assertEquals(Int.MAX_VALUE - 1, almostFull.nextElementCounter())
        assertSame(almostFull, almostFull.withDuplicatedNode("home"))
        assertEquals(2, almostFull.nodes.size)
        assertEquals(fixture().transitions, almostFull.transitions)
        assertEquals(0L, almostFull.revision)
    }

    private fun fixture(): EditorState {
        val home = ScreenNode("home", "Home", CanvasSize(1080, 1920), listOf(
            CanvasElement("hero", "image", name = "Hero", assetId = "shared", src = "image-data",
                x = 12f, y = -4f, w = 80f, h = 120f, locked = true, rotation = 12f,
                showDimensions = false, opacity = 63f),
            CanvasElement("button", "text", text = "Open", fontFamily = "brand-font", z = 1),
            CanvasElement("self", "text", text = "Again", z = 2),
            CanvasElement("back", "text", text = "Back", fontFamily = "Roboto", z = 3),
        ))
        val details = ScreenNode("details", "Details", CanvasSize(800, 500), listOf(
            CanvasElement("hero", "image", name = "Other", assetId = "other"),
            CanvasElement("details-image", "image", name = "Shared", assetId = "shared", z = 1),
            CanvasElement("button", "text", text = "Back", fontFamily = "Brand", z = 2),
        ))
        return FallenProject(
            id = "project-id",
            name = "Graph project",
            created = "2020-01-02T03:04:05Z",
            modified = "2021-02-03T04:05:06Z",
            nodes = listOf(home, details),
            startNodeId = "home",
            activeNodeId = "home",
            assets = listOf(Asset("shared", "Shared", "image-data"), Asset("other", "Other", "other-data")),
            fonts = listOf(ProjectFont("brand-font", "Brand", "font-data")),
            counter = 40,
            transitions = listOf(
                NodeTransition("home-hero", "home", "hero", toNodeId = "details"),
                NodeTransition("home-button", "home", "button", toNodeId = "details"),
                NodeTransition("home-self", "home", "self", toNodeId = "home"),
                NodeTransition("home-back", "home", "back", TransitionKind.BACK),
                NodeTransition("details-hero", "details", "hero", toNodeId = "home"),
                NodeTransition("details-image", "details", "details-image", toNodeId = "home"),
                NodeTransition("details-button", "details", "button", TransitionKind.BACK),
            ),
        ).toEditorState(sessionId = 7)
    }
}
