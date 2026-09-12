package com.fallen.studio.ui.screens.editor

import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.ProjectFont

/** Какая нижняя панель открыта */
enum class EditorPanel { NONE, ASSETS, TEXT, LAYERS, PROPERTIES, EXPORT }

/** Состояние редактора; не зависит от Android, чтобы проверять сохранение JVM-тестами. */
data class EditorState(
    val projectId: String? = null,
    val projectName: String = "Мой проект",
    val version: Int = 3,
    val created: String = "",
    val modified: String? = null,
    val canvas: CanvasSize = CanvasSize(),
    val assets: List<Asset> = emptyList(),
    val elements: List<CanvasElement> = emptyList(),
    val fonts: List<ProjectFont> = emptyList(),
    val counter: Int = 0,
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

    val isDirty: Boolean
        get() = revision != savedRevision

    val saveInProgress: Boolean
        get() = isSaving || isManualSavePending
}

/** Только изменения проекта двигают ревизию: выбор, панели и toast на неё не влияют. */
internal fun EditorState.withProjectEdit(): EditorState = copy(revision = revision + 1)

internal data class EditorSaveToken(val sessionId: Long, val revision: Long)

internal fun EditorState.saveToken(): EditorSaveToken = EditorSaveToken(sessionId, revision)

/** Manual-save временно обнуляет сигнал: после его окончания dirty снова запускает debounce. */
internal fun EditorState.autosaveToken(enabled: Boolean): EditorSaveToken? =
    if (enabled && isSessionReady && isDirty && !isManualSavePending &&
        !showExitDialog && !isExitRequested
    ) saveToken() else null

internal fun EditorState.toProjectSnapshot(): FallenProject = FallenProject(
    version = version,
    id = projectId,
    name = projectName,
    created = created,
    modified = modified,
    canvas = canvas,
    assets = assets,
    elements = elements,
    fonts = fonts,
    counter = counter,
)

internal fun FallenProject.toEditorState(sessionId: Long): EditorState = EditorState(
    projectId = id,
    projectName = name,
    version = version,
    created = created,
    modified = modified,
    canvas = canvas,
    assets = assets,
    elements = elements.sortedBy { it.z },
    fonts = fonts,
    counter = counter,
    sessionId = sessionId,
    isSessionReady = true,
)

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
