package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.data.model.ProjectGraph
import com.fallen.studio.data.model.ScreenNode
import com.fallen.studio.data.model.NodeTransition
import com.fallen.studio.data.model.TransitionKind

/** Какая нижняя панель открыта */
enum class EditorPanel { NONE, ASSETS, TEXT, LAYERS, PROPERTIES, EXPORT, NODES }

/** Состояние редактора; не зависит от Android, чтобы проверять сохранение JVM-тестами. */
data class EditorState(
    val projectId: String? = null,
    val projectName: String = "Мой проект",
    val version: Int = ProjectGraph.VERSION,
    val created: String = "",
    val modified: String? = null,
    val canvas: CanvasSize = CanvasSize(),
    val assets: List<Asset> = emptyList(),
    val elements: List<CanvasElement> = emptyList(),
    val fonts: List<ProjectFont> = emptyList(),
    val counter: Int = 0,
    val nodes: List<ScreenNode> = emptyList(),
    val activeNodeId: String? = null,
    val startNodeId: String? = null,
    val transitions: List<NodeTransition> = emptyList(),
    val previewNodeId: String? = null,
    val previewHistory: List<String> = emptyList(),
    // Invalidate work started before a preview round trip, even within the same session.
    val authoringEpoch: Long = 0,
    val selectedId: String? = null,
    val activePanel: EditorPanel = EditorPanel.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val sessionId: Long = 0,
    val revision: Long = 0,
    val savedRevision: Long = 0,
    val isSessionReady: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isManualSavePending: Boolean = false,
    val showSaveDialog: Boolean = false,
    val showExitDialog: Boolean = false,
    val isExitRequested: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val toast: String? = null,
) {
    val selectedElement: CanvasElement?
        get() = elements.find { it.id == selectedId }

    val isPreview: Boolean
        get() = previewNodeId != null

    val activeNode: ScreenNode?
        get() = nodes.find { it.id == activeNodeId }

    val previewNode: ScreenNode?
        get() = nodes.find { it.id == previewNodeId }

    val canPreviewBack: Boolean
        get() = isPreview && previewHistory.any { id -> nodes.any { it.id == id } }

    internal val canEditProject: Boolean
        get() = isSessionReady && !isLoading && !isPreview && !isExitRequested

    val isDirty: Boolean
        get() = revision != savedRevision

    val saveInProgress: Boolean
        get() = isSaving || isManualSavePending
}

/** A render-only projection. The live state always keeps the authoring canvas/selection. */
fun EditorState.forCanvas(): EditorState {
    if (!isPreview) return this
    val node = previewNode
    return copy(
        canvas = node?.canvas ?: canvas,
        elements = node?.elements ?: emptyList(),
        selectedId = null,
    )
}

/** Flush the legacy editing fields into just the active node; never touch its siblings. */
private fun EditorState.syncActiveNode(): EditorState {
    if (nodes.isEmpty()) {
        val graph = ProjectGraph.normalize(FallenProject(canvas = canvas, elements = elements))
        return copy(nodes = graph.nodes, activeNodeId = graph.activeNodeId, startNodeId = graph.startNodeId)
    }
    val active = activeNode ?: nodes.firstOrNull { it.id == startNodeId } ?: nodes.first()
    val updated = if (active.canvas == canvas && active.elements == elements) active
        else active.copy(canvas = canvas, elements = elements)
    return copy(
        nodes = if (updated === active) nodes else nodes.map { if (it.id == active.id) updated else it },
        activeNodeId = active.id,
        startNodeId = startNodeId?.takeIf { id -> nodes.any { it.id == id } } ?: nodes.first().id,
    )
}

/** Removing an element/node also removes its actions. Cycles and self-links are valid. */
internal fun pruneNodeTransitions(
    nodes: List<ScreenNode>,
    transitions: List<NodeTransition>,
): List<NodeTransition> {
    val elementsByNode = nodes.associate { node -> node.id to node.elements.map { it.id }.toSet() }
    val ids = mutableSetOf<String>()
    val sources = mutableSetOf<Pair<String, String>>()
    val valid = transitions.filter { edge ->
        val sourceExists = elementsByNode[edge.fromNodeId]?.contains(edge.elementId) == true
        val targetExists = edge.kind == TransitionKind.BACK || edge.toNodeId in elementsByNode
        sourceExists && targetExists && edge.id.isNotBlank() &&
            ids.add(edge.id) && sources.add(edge.fromNodeId to edge.elementId)
    }.map { edge ->
        if (edge.kind == TransitionKind.BACK && edge.toNodeId != null) edge.copy(toNodeId = null) else edge
    }
    return if (valid == transitions) transitions else valid
}

/** Only project edits advance revision. Every edit immediately updates the active-node mirror. */
internal fun EditorState.withProjectEdit(): EditorState {
    if (isPreview) return this
    val synced = syncActiveNode()
    return synced.copy(
        transitions = pruneNodeTransitions(synced.nodes, synced.transitions),
        selectedId = selectedId?.takeIf { id -> synced.elements.any { it.id == id } },
        revision = revision + 1,
    )
}

/** Install graph edits together with their active mirror, before calling withProjectEdit. */
private fun EditorState.withGraphEdit(
    newNodes: List<ScreenNode>,
    newActiveId: String? = activeNodeId,
    newStartId: String? = startNodeId,
    newTransitions: List<NodeTransition> = transitions,
): EditorState {
    val active = newNodes.firstOrNull { it.id == newActiveId } ?: newNodes.firstOrNull() ?: return this
    return copy(
        nodes = newNodes,
        activeNodeId = active.id,
        startNodeId = newStartId?.takeIf { id -> newNodes.any { it.id == id } } ?: newNodes.first().id,
        canvas = active.canvas,
        elements = active.elements,
        transitions = newTransitions,
        selectedId = selectedId.takeIf { active.id == activeNodeId },
    ).withProjectEdit()
}

private fun availableId(prefix: String, occupied: Set<String>): String =
    generateSequence(1) { it + 1 }.map { "$prefix$it" }.first { it !in occupied }

internal fun EditorState.nextElementCounter(): Int? {
    val ids = nodes.flatMap { it.elements }.mapTo(mutableSetOf()) { it.id }
    ids.addAll(elements.map { it.id })
    var next = counter.coerceAtLeast(0)
    do {
        // ProjectGraph reserves Int.MAX_VALUE: do not create an unsaveable or wrapped counter.
        if (next >= Int.MAX_VALUE - 1) return null
        next++
    } while ("el$next" in ids)
    return next
}

internal fun EditorState.withCreatedNode(name: String, width: Int, height: Int): EditorState {
    if (!canEditProject || name.isBlank() || width !in 100..10000 || height !in 100..10000) return this
    val node = ScreenNode(
        id = availableId("node", nodes.mapTo(mutableSetOf()) { it.id }),
        name = name.trim(),
        canvas = CanvasSize(width, height),
        elements = emptyList(),
    )
    return withGraphEdit(nodes + node, newActiveId = node.id)
}

/** activeNodeId is persisted; switching it is a revision, but element selection is not. */
internal fun EditorState.withSelectedNode(id: String): EditorState {
    if (!canEditProject || id == activeNodeId || nodes.none { it.id == id }) return this
    val synced = syncActiveNode()
    return synced.withGraphEdit(newNodes = synced.nodes, newActiveId = id)
}

internal fun EditorState.withRenamedNode(id: String, name: String): EditorState {
    val trimmed = name.trim()
    if (!canEditProject || trimmed.isEmpty() || nodes.none { it.id == id && it.name != trimmed }) return this
    return withGraphEdit(nodes.map { if (it.id == id) it.copy(name = trimmed) else it })
}

internal fun EditorState.withDuplicatedNode(id: String): EditorState {
    if (!canEditProject) return this
    val original = nodes.firstOrNull { it.id == id } ?: return this
    val newId = availableId("node", nodes.mapTo(mutableSetOf()) { it.id })
    val names = nodes.map { it.name }.toSet()
    val baseName = "${original.name} (копия)"
    var name = baseName
    var suffix = 2
    while (name in names) name = "$baseName ${suffix++}"
    val occupied = nodes.flatMap { it.elements }.mapTo(mutableSetOf()) { it.id }
    var newCounter = counter.coerceAtLeast(0)
    val elementIds = mutableMapOf<String, String>()
    val copiedElements = original.elements.map { element ->
        do {
            if (newCounter >= Int.MAX_VALUE - 1) return this
            newCounter++
        } while ("el$newCounter" in occupied)
        val elementId = "el$newCounter"
        occupied.add(elementId)
        elementIds[element.id] = elementId
        element.copy(id = elementId)
    }
    val transitionIds = transitions.mapTo(mutableSetOf()) { it.id }
    val actions = transitions.filter { it.fromNodeId == id }.mapNotNull { edge ->
        val sourceId = elementIds[edge.elementId] ?: return@mapNotNull null
        val edgeId = availableId("transition", transitionIds)
        transitionIds.add(edgeId)
        edge.copy(
            id = edgeId,
            fromNodeId = newId,
            elementId = sourceId,
            toNodeId = when {
                edge.kind == TransitionKind.BACK -> null
                edge.toNodeId == id -> newId
                else -> edge.toNodeId
            },
        )
    }
    val node = original.copy(id = newId, name = name, elements = copiedElements)
    return copy(counter = newCounter).withGraphEdit(
        newNodes = nodes + node,
        newActiveId = newId,
        newTransitions = transitions + actions,
    )
}

internal fun EditorState.withDeletedNode(id: String): EditorState {
    if (!canEditProject || nodes.size <= 1 || nodes.none { it.id == id }) return this
    val remaining = nodes.filterNot { it.id == id }
    return withGraphEdit(
        newNodes = remaining,
        newTransitions = transitions.filterNot { it.fromNodeId == id || it.toNodeId == id },
    )
}

internal fun EditorState.withStartNode(id: String): EditorState {
    if (!canEditProject || id == startNodeId || nodes.none { it.id == id }) return this
    return copy(startNodeId = id).withProjectEdit()
}

internal fun EditorState.withElementAction(
    elementId: String,
    kind: TransitionKind,
    targetNodeId: String? = null,
): EditorState {
    if (!canEditProject || elements.none { it.id == elementId }) return this
    val source = activeNodeId ?: return this
    if (kind == TransitionKind.OPEN_SCREEN && nodes.none { it.id == targetNodeId }) return this
    val target = if (kind == TransitionKind.BACK) null else targetNodeId
    val existing = transitions.firstOrNull { it.fromNodeId == source && it.elementId == elementId }
    if (existing != null && existing.kind == kind && existing.toNodeId == target) return this
    val action = NodeTransition(
        id = existing?.id ?: availableId("transition", transitions.mapTo(mutableSetOf()) { it.id }),
        fromNodeId = source,
        elementId = elementId,
        kind = kind,
        toNodeId = target,
    )
    return copy(transitions = transitions.filterNot {
        it.fromNodeId == source && it.elementId == elementId
    } + action).withProjectEdit()
}

internal fun EditorState.withClearedElementAction(elementId: String): EditorState {
    if (!canEditProject) return this
    val remaining = transitions.filterNot { it.fromNodeId == activeNodeId && it.elementId == elementId }
    return if (remaining.size == transitions.size) this else copy(transitions = remaining).withProjectEdit()
}

internal fun EditorState.withRenamedAsset(id: String, name: String): EditorState {
    if (!canEditProject || name.isBlank() || assets.none { it.id == id }) return this
    val newName = name.trim()
    val renamedAssets = assets.map { if (it.id == id) it.copy(name = newName) else it }
    val renamedNodes = nodes.map { node ->
        node.copy(elements = node.elements.map { if (it.assetId == id) it.copy(name = newName) else it })
    }
    if (renamedAssets == assets && renamedNodes == nodes) return this
    return copy(assets = renamedAssets).withGraphEdit(renamedNodes)
}

internal fun EditorState.withDeletedAsset(id: String): EditorState {
    if (!canEditProject || assets.none { it.id == id }) return this
    return copy(assets = assets.filterNot { it.id == id }).withGraphEdit(nodes.map { node ->
        node.copy(elements = node.elements.filterNot { it.assetId == id })
    })
}

internal fun EditorState.withDeletedFont(id: String): EditorState {
    if (!canEditProject) return this
    val font = fonts.firstOrNull { it.id == id } ?: return this
    return copy(fonts = fonts.filterNot { it.id == id }).withGraphEdit(nodes.map { node ->
        node.copy(elements = node.elements.map { element ->
            if (element.fontFamily == font.id || element.fontFamily == font.name) {
                element.copy(fontFamily = "Inter")
            } else element
        })
    })
}

/** Undo stores canvas/graph/resources, not the save-dialog name, IO metadata, revisions or UI flags. */
internal data class EditorSnapshot(
    val canvas: CanvasSize,
    val assets: List<Asset>,
    val elements: List<CanvasElement>,
    val fonts: List<ProjectFont>,
    val counter: Int,
    val nodes: List<ScreenNode>,
    val activeNodeId: String?,
    val startNodeId: String?,
    val transitions: List<NodeTransition>,
)

internal fun EditorState.undoSnapshot(): EditorSnapshot = EditorSnapshot(
    canvas, assets, elements, fonts, counter, nodes, activeNodeId, startNodeId, transitions,
)

internal fun EditorState.restoreSnapshot(snapshot: EditorSnapshot): EditorState {
    if (!canEditProject) return this
    return copy(
        canvas = snapshot.canvas,
        assets = snapshot.assets,
        elements = snapshot.elements,
        fonts = snapshot.fonts,
        counter = snapshot.counter,
        nodes = snapshot.nodes,
        activeNodeId = snapshot.activeNodeId,
        startNodeId = snapshot.startNodeId,
        transitions = snapshot.transitions,
        selectedId = selectedId.takeIf { activeNodeId == snapshot.activeNodeId },
    ).withProjectEdit()
}

/** Epoch is deliberately not a project field or an undo field. */
internal data class EditorAuthoringToken(val sessionId: Long, val epoch: Long)

internal fun EditorState.authoringToken(): EditorAuthoringToken? =
    if (canEditProject) EditorAuthoringToken(sessionId, authoringEpoch) else null

internal fun EditorState.acceptsAuthoringResult(token: EditorAuthoringToken): Boolean =
    canEditProject && token.sessionId == sessionId && token.epoch == authoringEpoch

/**
 * One picker slot, owned by the VM and accessed on the UI thread. Capture before launch;
 * consume before URI IO, including cancellation. Do not reset on preview/session changes:
 * an invalid request must occupy its slot until its callback, which carries no request ID.
 * Otherwise a late URI could consume a newer request's token. Process recreation starts empty.
 */
internal class EditorImportRequest {
    private var pendingToken: EditorAuthoringToken? = null

    fun begin(state: EditorState): Boolean {
        if (pendingToken != null) return false
        val token = state.authoringToken() ?: return false
        pendingToken = token
        return true
    }

    fun complete(state: EditorState, hasResult: Boolean): EditorAuthoringToken? {
        val token = pendingToken
        pendingToken = null
        return token?.takeIf { hasResult && state.acceptsAuthoringResult(it) }
    }
}

internal const val PREVIEW_HISTORY_LIMIT = 100

internal fun EditorState.withPreviewStarted(): EditorState {
    // A started write must reconcile normally. Do not enter preview until it is finished.
    if (!canEditProject || saveInProgress || showSaveDialog || showExitDialog) return this
    val start = nodes.firstOrNull { it.id == startNodeId } ?: return this
    return copy(previewNodeId = start.id, previewHistory = emptyList(), authoringEpoch = authoringEpoch + 1)
}

internal fun EditorState.withPreviewStopped(): EditorState =
    if (!isPreview) this else copy(
        previewNodeId = null,
        previewHistory = emptyList(),
        authoringEpoch = authoringEpoch + 1,
    )

internal fun EditorState.withPreviewBack(): EditorState {
    if (!isPreview) return this
    val index = previewHistory.indexOfLast { id -> nodes.any { it.id == id } }
    if (index < 0) return this
    return copy(previewNodeId = previewHistory[index], previewHistory = previewHistory.take(index))
}

internal fun EditorState.withPreviewElementActivated(elementId: String): EditorState {
    val node = previewNode ?: return this
    if (node.elements.none { it.id == elementId }) return this
    val action = transitions.firstOrNull { it.fromNodeId == node.id && it.elementId == elementId } ?: return this
    if (action.kind == TransitionKind.BACK) return withPreviewBack()
    val target = nodes.firstOrNull { it.id == action.toNodeId } ?: return this
    return copy(
        previewNodeId = target.id,
        previewHistory = (previewHistory + node.id).takeLast(PREVIEW_HISTORY_LIMIT),
    )
}

internal data class EditorSaveToken(val sessionId: Long, val revision: Long, val authoringEpoch: Long = 0)

internal fun EditorState.saveToken(): EditorSaveToken = EditorSaveToken(sessionId, revision, authoringEpoch)

/** Manual-save/preview temporarily clear the signal; dirty rearms debounce when they finish. */
internal fun EditorState.autosaveToken(enabled: Boolean): EditorSaveToken? =
    if (enabled && canEditProject && isDirty && !isManualSavePending && !showExitDialog
    ) saveToken() else null

/** Exports and saves always use authoring nodes, even when called on a forCanvas() projection. */
internal fun EditorState.toProjectSnapshot(): FallenProject {
    val authoring = if (isPreview) this else syncActiveNode()
    val active = authoring.activeNode
    return ProjectGraph.normalize(FallenProject(
        version = version,
        id = projectId,
        name = projectName,
        created = created,
        modified = modified,
        canvas = active?.canvas ?: canvas,
        assets = assets,
        elements = active?.elements ?: elements,
        fonts = fonts,
        counter = counter,
        nodes = authoring.nodes,
        activeNodeId = authoring.activeNodeId,
        startNodeId = authoring.startNodeId,
        transitions = authoring.transitions,
    ))
}

internal fun FallenProject.toEditorState(sessionId: Long): EditorState {
    val project = ProjectGraph.normalize(this)
    val nodes = project.nodes.map { it.copy(elements = it.elements.sortedBy { element -> element.z }) }
    val active = nodes.first { it.id == project.activeNodeId }
    return EditorState(
        projectId = project.id,
        projectName = project.name,
        version = project.version,
        created = project.created,
        modified = project.modified,
        canvas = active.canvas,
        assets = project.assets,
        elements = active.elements,
        fonts = project.fonts,
        counter = project.counter,
        nodes = nodes,
        activeNodeId = project.activeNodeId,
        startNodeId = project.startNodeId,
        transitions = project.transitions,
        sessionId = sessionId,
        isSessionReady = true,
    )
}

/**
 * Подтверждаем только записанную ревизию, не подменяя содержимое текущего черновика.
 * Даже при новых правках принимаем выданный хранилищем ID и даты: следующий save
 * должен обновить тот же файл, а не создать второй проект с пустым ID.
 */
internal fun reconcileSavedProject(
    current: EditorState,
    token: EditorSaveToken,
    saved: FallenProject,
): EditorState {
    if (current.sessionId != token.sessionId ||
        token.revision < current.savedRevision || token.revision > current.revision
    ) return current

    require(!saved.id.isNullOrBlank()) { "Saved snapshot must have a project id" }
    return current.copy(
        projectId = saved.id,
        version = saved.version,
        created = saved.created,
        modified = saved.modified,
        savedRevision = token.revision,
    )
}
