package com.fallen.studio.ui.screens.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fallen.studio.data.AppSettings
import com.fallen.studio.data.ProjectRepository
import com.fallen.studio.data.SettingsRepository
import com.fallen.studio.data.model.Asset
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.CanvasSize
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.data.model.TransitionKind
import com.fallen.studio.util.FontManager
import com.fallen.studio.util.Haptics
import com.fallen.studio.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Эффективный шаг сетки: подстраивается под разрешение холста так,
 * чтобы линии РОВНО делили холст без остатка. Одна и та же формула
 * используется и при отрисовке сетки, и при привязке к ней —
 * поэтому границы элементов всегда совпадают с линиями.
 */
fun effectiveGridStep(canvasSide: Int, gridSize: Int): Float {
    val base = gridSize.coerceAtLeast(10).toFloat()
    val cells = Math.round(canvasSide / base).coerceAtLeast(1)
    return canvasSide / cells.toFloat()
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private val saveMutex = Mutex()
    private var loadJob: Job? = null
    private var sessionInitialized = false
    private var nextSessionId = 0L
    private var autosaveGeneration = 0L
    private var exitRequestConsumed = false
    private var loadRequest: LoadRequest? = null
    // VM-owned slots survive screen recreation; stale requests stay reserved until their callback.
    private val assetImportRequest = EditorImportRequest()
    private val fontImportRequest = EditorImportRequest()

    private data class LoadRequest(val id: String?, val canvasW: Int, val canvasH: Int)

    init {
        startAutosave()
    }

    // ---------- Загрузка / сохранение ----------

    /**
     * Одна ViewModel принадлежит одной записи навигации. Повторный LaunchedEffect
     * после recreation не заменяет активную RAM-сессию (включая новый проект без ID).
     * Это не восстановление несохранённого черновика после завершения процесса.
     */
    fun loadProject(id: String?, canvasW: Int = 0, canvasH: Int = 0) {
        if (sessionInitialized || _state.value.isPreview || _state.value.isExitRequested) return
        sessionInitialized = true
        val request = LoadRequest(id, canvasW, canvasH)
        loadRequest = request
        beginLoad(request)
    }

    fun retryLoadProject() {
        val request = loadRequest ?: return
        if (_state.value.isLoading || _state.value.isSessionReady || _state.value.isExitRequested) return
        beginLoad(request)
    }

    private fun beginLoad(request: LoadRequest) {
        resetGestures()
        loadJob?.cancel()
        val sessionId = ++nextSessionId
        exitRequestConsumed = false
        undoStack.clear()
        redoStack.clear()
        _state.value = EditorState(sessionId = sessionId, isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                val project = if (request.id == null) {
                    val config = settings.value
                    FallenProject(
                        name = "Мой проект",
                        canvas = CanvasSize(
                            if (request.canvasW > 0) request.canvasW else config.defaultCanvasW,
                            if (request.canvasH > 0) request.canvasH else config.defaultCanvasH,
                        ),
                    )
                } else {
                    repository.load(request.id)
                }
                if (_state.value.sessionId != sessionId || _state.value.isExitRequested) return@launch
                if (project == null) {
                    _state.value = _state.value.copy(
                        loadError = "Не удалось открыть проект. Файл отсутствует, повреждён или недоступен.",
                    )
                } else {
                    applyProject(project, sessionId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.sessionId == sessionId) {
                    _state.value = _state.value.copy(loadError = "Не удалось открыть проект. Попробуйте ещё раз.")
                }
            } finally {
                if (_state.value.sessionId == sessionId) {
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }
    }

    fun importProject(project: FallenProject) {
        if (_state.value.isPreview || _state.value.isExitRequested) return
        loadJob?.cancel()
        sessionInitialized = true
        loadRequest = null
        exitRequestConsumed = false
        applyProject(project.copy(id = null), ++nextSessionId)
        _state.value = _state.value.withProjectEdit()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoFlags()
        showToast("Проект импортирован")
    }

    private fun applyProject(project: FallenProject, sessionId: Long) {
        resetGestures()
        _state.value = project.toEditorState(sessionId)
    }

    fun currentProject(): FallenProject = _state.value.toProjectSnapshot()

    /** onSaved подтверждает записанный снимок; новые правки могут всё ещё быть dirty. */
    fun saveProject(name: String? = null, onSaved: (String) -> Unit = {}) {
        requestSave(name = name, onSaved = onSaved)
    }

    fun saveAndExit() {
        if (!_state.value.showExitDialog) return
        requestSave(exitAfterSave = true)
    }

    private fun requestSave(
        name: String? = null,
        exitAfterSave: Boolean = false,
        onSaved: (String) -> Unit = {},
    ) {
        val s = _state.value
        if (!s.canEditProject || s.isManualSavePending) return
        // Имя — такая же правка проекта: при ошибке оно остаётся в dirty-черновике.
        val edited = if (name != null && name != s.projectName) {
            s.copy(projectName = name).withProjectEdit()
        } else s
        val sessionId = edited.sessionId
        // Устанавливаем до launch, чтобы быстрые повторные нажатия не ставили save в очередь.
        _state.value = edited.copy(isManualSavePending = true, saveError = null)
        viewModelScope.launch {
            try {
                val saved = persistProject(sessionId, automatic = false) ?: return@launch
                if (_state.value.sessionId != sessionId || _state.value.isExitRequested) return@launch
                val hasNewEdits = _state.value.isDirty
                _state.value = _state.value.copy(showSaveDialog = false)
                if (exitAfterSave) {
                    if (hasNewEdits) {
                        _state.value = _state.value.copy(
                            saveError = "Во время сохранения появились новые изменения. Сохраните их перед выходом.",
                        )
                    } else {
                        leaveEditor()
                    }
                }
                haptic(strong = true)
                showToast(if (hasNewEdits) "Снимок сохранён. Есть новые несохранённые изменения." else "Проект сохранён")
                onSaved(requireNotNull(saved.id))
            } finally {
                if (_state.value.sessionId == sessionId) {
                    _state.value = _state.value.copy(isManualSavePending = false)
                }
            }
        }
    }

    /**
     * Общая очередь manual/auto. Снимок берётся ПОСЛЕ захвата mutex: ожидающий save
     * увидит ID и metadata первой записи нового проекта, а не создаст дубликат.
     */
    private suspend fun persistProject(
        sessionId: Long,
        automatic: Boolean,
        expectedRevision: Long? = null,
        expectedAutosaveGeneration: Long? = null,
        expectedAuthoringEpoch: Long? = null,
    ): FallenProject? = saveMutex.withLock {
        val s = _state.value
        if (s.sessionId != sessionId || !s.canEditProject) return@withLock null
        if (automatic && (!settings.value.autosaveEnabled || !s.isDirty || s.showExitDialog ||
                s.isManualSavePending || s.revision != expectedRevision ||
                expectedAutosaveGeneration != autosaveGeneration || s.authoringEpoch != expectedAuthoringEpoch)
        ) return@withLock null
        val token = s.saveToken()
        val snapshot = s.toProjectSnapshot()
        _state.value = s.copy(isSaving = true, saveError = null)
        try {
            val saved = repository.saveSnapshot(snapshot)
            _state.value = reconcileSavedProject(_state.value, token, saved)
            saved
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (_state.value.sessionId == sessionId) {
                _state.value = _state.value.copy(
                    saveError = "Не удалось сохранить проект. Изменения остаются в редакторе; попробуйте ещё раз.",
                )
            }
            null
        } finally {
            if (_state.value.sessionId == sessionId) {
                _state.value = _state.value.copy(isSaving = false)
            }
        }
    }

    /** Отменяется только таймер debounce, но не уже начавшаяся запись в хранилище. */
    private fun startAutosave() {
        viewModelScope.launch {
            _state.combine(settings) { s, config ->
                s.autosaveToken(config.autosaveEnabled)
            }.distinctUntilChanged().collectLatest { token ->
                val generation = ++autosaveGeneration
                if (token != null) {
                    delay(1500)
                    // Не дочерняя задача collectLatest: новая правка не отменяет IO
                    // между присвоением нового ID на диске и reconciliation в RAM.
                    viewModelScope.launch {
                        persistProject(
                            token.sessionId,
                            automatic = true,
                            expectedRevision = token.revision,
                            expectedAutosaveGeneration = generation,
                            expectedAuthoringEpoch = token.authoringEpoch,
                        )
                    }
                }
            }
        }
    }

    fun openSaveDialog() {
        val s = _state.value
        if (!s.canEditProject || s.saveInProgress || s.showExitDialog) return
        _state.value = s.copy(showSaveDialog = true, saveError = null)
    }

    fun dismissSaveDialog() {
        if (!_state.value.isManualSavePending) {
            _state.value = _state.value.copy(showSaveDialog = false)
        }
    }

    fun dismissSaveError() {
        if (!_state.value.saveInProgress) _state.value = _state.value.copy(saveError = null)
    }

    fun requestExit() {
        val s = _state.value
        when {
            s.isPreview -> stopPreview()
            s.isExitRequested -> Unit
            s.activePanel != EditorPanel.NONE -> closePanel()
            s.showSaveDialog -> dismissSaveDialog()
            s.isDirty || s.saveInProgress -> _state.value = s.copy(showExitDialog = true)
            else -> leaveEditor()
        }
    }

    fun dismissExitDialog() {
        if (!_state.value.isManualSavePending) {
            _state.value = _state.value.copy(showExitDialog = false)
        }
    }

    fun discardAndExit() {
        if (_state.value.isPreview) {
            stopPreview()
            return
        }
        // Уже начавшуюся запись нельзя выдать за отменённую. Ждём её завершения.
        if (_state.value.saveInProgress) return
        leaveEditor()
    }

    private fun leaveEditor() {
        resetGestures()
        loadJob?.cancel()
        _state.value = _state.value.copy(
            isExitRequested = true,
            showSaveDialog = false,
            showExitDialog = false,
        )
    }

    /** Навигацию выполняет текущий экран, а не callback, захваченный до recreation. */
    fun consumeExitRequest(): Boolean {
        if (!_state.value.isExitRequested || exitRequestConsumed) return false
        exitRequestConsumed = true
        return true
    }

    // ---------- Undo / Redo ----------

    private fun pushUndo() {
        val s = _state.value
        if (!s.canEditProject) return
        undoStack.addLast(s.undoSnapshot())
        val limit = settings.value.undoLimit.coerceAtLeast(5)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoFlags()
    }

    /** Pure operations produce a single revision; history flags always come from the live stacks. */
    private fun editProject(transform: (EditorState) -> EditorState): Boolean {
        val before = _state.value
        if (!before.canEditProject) return false
        val edited = transform(before)
        if (edited == before) return false
        pushUndo()
        _state.value = edited.copy(canUndo = _state.value.canUndo, canRedo = _state.value.canRedo)
        if (before.activeNodeId != edited.activeNodeId) resetGestures()
        return true
    }

    fun undo() {
        if (!_state.value.canEditProject) return
        val snapshot = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.undoSnapshot())
        restore(snapshot)
        haptic(strong = true)
        showToast("Отменено")
    }

    fun redo() {
        if (!_state.value.canEditProject) return
        val snapshot = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.undoSnapshot())
        restore(snapshot)
        haptic(strong = true)
        showToast("Возвращено")
    }

    private fun restore(snapshot: EditorSnapshot) {
        _state.value = _state.value.restoreSnapshot(snapshot)
        resetGestures()
        updateUndoRedoFlags()
    }

    private fun updateUndoRedoFlags() {
        _state.value = _state.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    // ---------- Канвас ----------

    fun setCanvasSize(w: Int, h: Int) {
        if (w !in 100..10000 || h !in 100..10000) return
        editProject { s ->
            if (s.canvas == CanvasSize(w, h)) s else s.copy(canvas = CanvasSize(w, h)).withProjectEdit()
        }
    }

    // ---------- Ноды и переходы ----------

    fun createNode(name: String, width: Int, height: Int) {
        editProject { it.withCreatedNode(name, width, height) }
    }

    fun selectNode(id: String) {
        editProject { it.withSelectedNode(id) }
    }

    fun renameNode(id: String, name: String) {
        editProject { it.withRenamedNode(id, name) }
    }

    fun duplicateNode(id: String) {
        if (editProject { it.withDuplicatedNode(id) }) showToast("Нод дублирован")
    }

    fun deleteNode(id: String) {
        if (!_state.value.canEditProject) return
        if (_state.value.nodes.size == 1 && _state.value.nodes.any { it.id == id }) {
            showToast("Нельзя удалить последний нод")
            return
        }
        if (editProject { it.withDeletedNode(id) }) showToast("Нод удалён")
    }

    fun setStartNode(id: String) {
        editProject { it.withStartNode(id) }
    }

    fun setElementTransition(elementId: String, targetNodeId: String) {
        editProject { it.withElementAction(elementId, TransitionKind.OPEN_SCREEN, targetNodeId) }
    }

    fun setElementBackAction(elementId: String) {
        editProject { it.withElementAction(elementId, TransitionKind.BACK) }
    }

    fun clearElementTransition(elementId: String) {
        editProject { it.withClearedElementAction(elementId) }
    }

    // ---------- Preview: no authoring fields, revisions or undo entries are changed ----------

    fun startPreview() {
        val before = _state.value
        val preview = before.withPreviewStarted()
        if (preview === before) return
        // Invalidate a debounce already waiting for the save mutex as well as stale gestures/imports.
        autosaveGeneration++
        resetGestures()
        _state.value = preview
    }

    fun stopPreview() {
        val before = _state.value
        val authoring = before.withPreviewStopped()
        if (authoring === before) return
        autosaveGeneration++
        resetGestures()
        _state.value = authoring
    }

    fun previewBack() {
        _state.value = _state.value.withPreviewBack()
    }

    fun activatePreviewElement(elementId: String) {
        _state.value = _state.value.withPreviewElementActivated(elementId)
    }

    // ---------- Выделение и панели ----------

    /** Тактильный отклик с учётом настройки «Вибрация» */
    private fun haptic(strong: Boolean = false) {
        if (!settings.value.hapticFeedback) return
        if (strong) Haptics.click(getApplication()) else Haptics.tick(getApplication())
    }

    fun select(id: String?) {
        val s = _state.value
        if (!s.canEditProject || (id != null && s.elements.none { it.id == id })) return
        if (id != s.selectedId) resetGestures()
        // Вибрация только при выборе элемента (не при снятии выделения)
        if (id != null && id != _state.value.selectedId) haptic()
        _state.value = _state.value.copy(selectedId = id)
    }

    fun openPanel(panel: EditorPanel) {
        if (!_state.value.canEditProject) return
        _state.value = _state.value.copy(
            activePanel = if (_state.value.activePanel == panel) EditorPanel.NONE else panel,
        )
    }

    fun closePanel() {
        if (!_state.value.canEditProject) return
        _state.value = _state.value.copy(activePanel = EditorPanel.NONE)
    }

    // ---------- Ассеты ----------

    /** Reserve the image request BEFORE launching the picker, never from its result callback. */
    fun beginAssetImport(): Boolean = assetImportRequest.begin(_state.value)

    /** Null (picker cancellation or launch failure) also consumes the pending request. */
    fun completeAssetImport(uri: Uri?) {
        val token = assetImportRequest.complete(_state.value, hasResult = uri != null) ?: return
        if (uri != null) importAsset(uri, token)
    }

    /** Direct URI API for callers that do not launch a picker. */
    fun addAssetFromUri(uri: Uri) {
        val token = _state.value.authoringToken() ?: return
        importAsset(uri, token)
    }

    private fun importAsset(uri: Uri, token: EditorAuthoringToken) {
        viewModelScope.launch {
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            val context = getApplication<Application>()
            val dataUrl = ImageUtils.uriToDataUrl(context, uri)
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            if (dataUrl == null) {
                showToast("Не удалось загрузить изображение")
                return@launch
            }
            val name = ImageUtils.fileNameFromUri(context, uri)
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            val asset = Asset(
                id = "a${System.currentTimeMillis()}_${(0..9999).random()}",
                name = name,
                src = dataUrl,
            )
            if (editProject {
                if (it.acceptsAuthoringResult(token)) it.copy(assets = it.assets + asset).withProjectEdit() else it
            }) showToast("Ассет «$name» добавлен")
        }
    }

    fun renameAsset(id: String, newName: String) {
        editProject { it.withRenamedAsset(id, newName) }
    }

    fun deleteAsset(id: String) {
        if (editProject { it.withDeletedAsset(id) }) showToast("Ассет удалён")
    }

    /** Добавить экземпляр ассета на канвас */
    fun placeAsset(assetId: String) {
        if (!_state.value.canEditProject) return
        val asset = _state.value.assets.find { it.id == assetId } ?: return
        val counter = _state.value.nextElementCounter() ?: return
        pushUndo()
        // Берём состояние после pushUndo, чтобы не вернуть устаревшие canUndo/canRedo.
        val s = _state.value

        // Определяем реальные пропорции изображения
        val bitmap = ImageUtils.decodeDataUrl(asset.src)
        var w = 200f
        var h = 200f
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val aspect = bitmap.width.toFloat() / bitmap.height
            if (aspect >= 1f) h = w / aspect else w = h * aspect
        }

        // При включённой «Привязке к сетке» края нового элемента
        // сразу выравниваются по линиям сетки
        val rect = snapRectToGrid(
            s.canvas.w / 2f - w / 2f,
            s.canvas.h / 2f - h / 2f,
            w,
            h,
        )

        val element = CanvasElement(
            id = "el$counter",
            type = "image",
            assetId = assetId,
            name = "${asset.name}_$counter",
            src = asset.src,
            x = rect[0],
            y = rect[1],
            w = rect[2],
            h = rect[3],
            z = s.elements.size,
        )
        _state.value = s.copy(
            elements = s.elements + element,
            counter = counter,
            selectedId = element.id,
            activePanel = EditorPanel.NONE,
        ).withProjectEdit()
        haptic(strong = true)
        showToast("«${asset.name}» добавлен")
    }

    // ---------- Шрифты ----------

    /** Reserve a separate font request before launching the picker. */
    fun beginFontImport(): Boolean = fontImportRequest.begin(_state.value)

    fun completeFontImport(uri: Uri?) {
        val token = fontImportRequest.complete(_state.value, hasResult = uri != null) ?: return
        if (uri != null) importFont(uri, token)
    }

    /** Загружает пользовательский шрифт (.ttf / .otf) в проект без открытия picker. */
    fun addFontFromUri(uri: Uri) {
        val token = _state.value.authoringToken() ?: return
        importFont(uri, token)
    }

    private fun importFont(uri: Uri, token: EditorAuthoringToken) {
        viewModelScope.launch {
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            val context = getApplication<Application>()
            val font = FontManager.fontFromUri(context, uri)
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            if (font == null) {
                showToast("Не удалось загрузить шрифт (нужен .ttf или .otf до 8 МБ)")
                return@launch
            }
            if (_state.value.fonts.any { it.name == font.name }) {
                showToast("Шрифт «${font.name}» уже добавлен")
                return@launch
            }
            if (editProject {
                if (it.acceptsAuthoringResult(token)) it.copy(fonts = it.fonts + font).withProjectEdit() else it
            }) showToast("Шрифт «${font.name}» добавлен")
        }
    }

    /** Удаляет общий шрифт; текст во всех нодах получает fallback. */
    fun deleteFont(id: String) {
        if (!editProject { it.withDeletedFont(id) }) return
        FontManager.invalidate(id)
        showToast("Шрифт удалён")
    }

    // ---------- Текст ----------

    fun addTextElement(
        text: String,
        fontSize: Int,
        fontWeight: String,
        color: String,
        textAlign: String,
        fontFamily: String = "Inter",
    ) {
        if (!_state.value.canEditProject) return
        if (text.isBlank()) {
            showToast("Введите текст")
            return
        }
        val counter = _state.value.nextElementCounter() ?: return
        pushUndo()
        val s = _state.value
        val element = CanvasElement(
            id = "el$counter",
            type = "text",
            name = "Text_$counter",
            text = text,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            textAlign = textAlign,
            strokeWidth = 0f,
            strokeColor = "#000000",
            lineHeight = 50f,
            letterSpacing = 50f,
            shadowEnabled = false,
            shadowBlur = 20f,
            shadowOpacity = 50f,
            shadowX = 50f,
            shadowY = 50f,
            shadowColor = "#000000",
            x = s.canvas.w / 2f - 150f,
            y = s.canvas.h / 2f - 30f,
            w = 300f,
            h = 60f,
            z = s.elements.size,
        )
        // Рамка нового текста сразу подгоняется под фактический размер текста,
        // а при включённой «Привязке к сетке» позиция выравнивается по линиям
        val fitted = fitTextBox(element).let { fit ->
            val cfg = settings.value
            if (cfg.snapToGrid) {
                val stepX = effectiveGridStep(s.canvas.w, cfg.gridSize)
                val stepY = effectiveGridStep(s.canvas.h, cfg.gridSize)
                fit.copy(
                    x = Math.round(fit.x / stepX) * stepX,
                    y = Math.round(fit.y / stepY) * stepY,
                )
            } else fit
        }
        _state.value = s.copy(
            elements = s.elements + fitted,
            counter = counter,
            selectedId = fitted.id,
            activePanel = EditorPanel.NONE,
        ).withProjectEdit()
        haptic(strong = true)
        showToast("Текст добавлен")
    }

    // ---------- Операции над элементами ----------

    /**
     * Привязывает ВСЕ четыре края прямоугольника к линиям сетки
     * (если включена «Привязка к сетке»). Используется при добавлении
     * элементов на холст и при пропорциональном масштабировании —
     * границы элемента всегда ровно лежат на сеточных линиях.
     */
    private fun snapRectToGrid(x: Float, y: Float, w: Float, h: Float): FloatArray {
        val cfg = settings.value
        if (!cfg.snapToGrid) return floatArrayOf(x, y, w, h)
        val s = _state.value
        val stepX = effectiveGridStep(s.canvas.w, cfg.gridSize)
        val stepY = effectiveGridStep(s.canvas.h, cfg.gridSize)
        val nx = Math.round(x / stepX) * stepX
        val ny = Math.round(y / stepY) * stepY
        // Правый/нижний края округляются к ближайшей линии,
        // ширина/высота — минимум одна клетка
        val right = (Math.round((x + w) / stepX) * stepX).coerceAtLeast(nx + stepX)
        val bottom = (Math.round((y + h) / stepY) * stepY).coerceAtLeast(ny + stepY)
        return floatArrayOf(nx, ny, right - nx, bottom - ny)
    }

    /**
     * ИСПРАВЛЕНИЕ «текст выходит за рамку»: высота рамки текстового
     * элемента всегда подгоняется под фактическую высоту текста
     * (измеряется тем же StaticLayout, которым текст рисуется).
     * Рамка растёт вниз, если текст переносится на новые строки.
     */
    private fun fitTextBox(el: CanvasElement): CanvasElement {
        if (!el.isText) return el
        val fonts = _state.value.fonts
        val measured = TextElementRenderer.measureHeight(el, fonts, getApplication())
        return if (measured > el.h) el.copy(h = measured) else el
    }

    fun updateElement(id: String, transform: (CanvasElement) -> CanvasElement, recordUndo: Boolean = true) {
        if (!_state.value.canEditProject) return
        val original = _state.value.elements.firstOrNull { it.id == id } ?: return
        val updated = fitTextBox(transform(original).copy(id = id))
        if (updated == original) return
        if (recordUndo) pushUndo()
        _state.value = _state.value.copy(
            elements = _state.value.elements.map { if (it.id == id) updated else it },
        ).withProjectEdit()
    }

    // Сырая (не «примагниченная») позиция элеме��та во время жеста.
    // Snap применяется к ней, а не к уже квантованной позиции —
    // иначе элемент дрейфует и «прилипает» к границам сам по себе.
    private var gestureRawX = 0f
    private var gestureRawY = 0f
    private var gestureElementId: String? = null
    private var gestureNodeId: String? = null
    private var gestureToken: EditorAuthoringToken? = null

    private fun resetGestures() {
        gestureElementId = null
        gestureNodeId = null
        gestureToken = null
        scaleStartW = 0f
        scaleStartH = 0f
        rotateStartAngle = 0f
    }

    private fun canContinueGesture(id: String): Boolean {
        val token = gestureToken ?: return false
        val s = _state.value
        return s.acceptsAuthoringResult(token) && s.activeNodeId == gestureNodeId && gestureElementId == id
    }

    private fun captureGesture(element: CanvasElement) {
        gestureToken = _state.value.authoringToken()
        gestureNodeId = _state.value.activeNodeId
        gestureElementId = element.id
    }

    /** Для непрерывных жестов: undo пишется один раз в начале жеста */
    fun beginGesture() {
        if (!_state.value.canEditProject) return
        val el = _state.value.selectedElement ?: return
        if (el.locked) return
        pushUndo()
        captureGesture(el)
        gestureRawX = el.x
        gestureRawY = el.y
    }

    fun moveElement(id: String, dx: Float, dy: Float) {
        if (!canContinueGesture(id)) return
        val s = _state.value
        val el = s.elements.find { it.id == id } ?: return
        if (el.locked) return

        gestureRawX += dx
        gestureRawY += dy

        var nx = gestureRawX
        var ny = gestureRawY
        val cfg = settings.value

        // ИСПРАВЛЕНИЕ КРИВОЙ ПРИВЯЗКИ: раньше магнитная привязка
        // (к краям холста и других элементов) применялась ВМЕСТЕ с сеточной
        // и сдвигала элемент с линии сетки уже после округления.
        // Теперь при включённой «Привязке к сетке» работает ТОЛЬКО сетка —
        // границы элемента всегда ровно лежат на сеточных линиях.
        if (cfg.snapToGrid) {
            val stepX = effectiveGridStep(s.canvas.w, cfg.gridSize)
            val stepY = effectiveGridStep(s.canvas.h, cfg.gridSize)
            nx = Math.round(nx / stepX) * stepX
            ny = Math.round(ny / stepY) * stepY
        } else if (cfg.snapEnabled) {
            // Магнитная привязка: к центру/краям холста и к границам
            // других элементов. Работает от сырой позиции, поэтому
            // элемент легко «отлипает» при дальнейшем движении.
            val snapped = applySnap(nx, ny, el.w, el.h, id, s)
            nx = snapped.first
            ny = snapped.second
        }

        _state.value = s.copy(
            elements = s.elements.map {
                if (it.id == id) it.copy(x = nx, y = ny) else it
            },
        ).withProjectEdit()
    }

    /**
     * Магнитная привязка из оригинальной программы:
     * центр и края холста (чувствительность snapCanvasSensitivity),
     * границы и центры других элементов (snapElementsSensitivity).
     */
    private fun applySnap(
        rawX: Float,
        rawY: Float,
        w: Float,
        h: Float,
        currentId: String,
        s: EditorState,
    ): Pair<Float, Float> {
        val cfg = settings.value
        val tC = cfg.snapCanvasSensitivity.toFloat()
        val tE = cfg.snapElementsSensitivity.toFloat()
        var x = rawX
        var y = rawY

        val cx = x + w / 2f
        val cy = y + h / 2f
        val ccx = s.canvas.w / 2f
        val ccy = s.canvas.h / 2f

        // Центр холста
        if (kotlin.math.abs(cx - ccx) < tC) x = ccx - w / 2f
        if (kotlin.math.abs(cy - ccy) < tC) y = ccy - h / 2f
        // Края холста
        if (kotlin.math.abs(x) < tC) x = 0f
        if (kotlin.math.abs(x + w - s.canvas.w) < tC) x = s.canvas.w - w
        if (kotlin.math.abs(y) < tC) y = 0f
        if (kotlin.math.abs(y + h - s.canvas.h) < tC) y = s.canvas.h - h

        // Границы и центры других элементов
        for (o in s.elements) {
            if (o.id == currentId) continue
            val ocx = o.x + o.w / 2f
            val ocy = o.y + o.h / 2f
            val ncx = x + w / 2f
            val ncy = y + h / 2f
            if (kotlin.math.abs(ncx - ocx) < tE) x = ocx - w / 2f
            if (kotlin.math.abs(ncy - ocy) < tE) y = ocy - h / 2f
            if (kotlin.math.abs(x - o.x) < tE) x = o.x
            if (kotlin.math.abs(x + w - (o.x + o.w)) < tE) x = o.x + o.w - w
            if (kotlin.math.abs(x - (o.x + o.w)) < tE) x = o.x + o.w
            if (kotlin.math.abs(x + w - o.x) < tE) x = o.x - w
            if (kotlin.math.abs(y - o.y) < tE) y = o.y
            if (kotlin.math.abs(y + h - (o.y + o.h)) < tE) y = o.y + o.h - h
            if (kotlin.math.abs(y - (o.y + o.h)) < tE) y = o.y + o.h
            if (kotlin.math.abs(y + h - o.y) < tE) y = o.y - h
        }

        return x to y
    }

    fun resizeElement(id: String, newX: Float, newY: Float, newW: Float, newH: Float) {
        if (!canContinueGesture(id)) return
        var x = newX
        var y = newY
        var w = newW
        var h = newH

        // Привязка краёв рамки к сетке при ресайзе — границы элемента
        // совпадают с линиями сетки холста
        val cfg = settings.value
        if (cfg.snapToGrid) {
            val s = _state.value
            val stepX = effectiveGridStep(s.canvas.w, cfg.gridSize)
            val stepY = effectiveGridStep(s.canvas.h, cfg.gridSize)
            val right = Math.round((x + w) / stepX) * stepX
            val bottom = Math.round((y + h) / stepY) * stepY
            x = Math.round(x / stepX) * stepX
            y = Math.round(y / stepY) * stepY
            w = right - x
            h = bottom - y
        }

        _state.value = _state.value.copy(
            elements = _state.value.elements.map { el ->
                if (el.id == id && !el.locked) {
                    fitTextBox(
                        el.copy(
                            x = x,
                            y = y,
                            w = w.coerceAtLeast(10f),
                            h = h.coerceAtLeast(10f),
                        ),
                    )
                } else el
            },
        ).withProjectEdit()
    }

    // Размеры и шрифт элемента на старте жеста масштабирования
    private var scaleStartW = 0f
    private var scaleStartH = 0f
    private var scaleStartFontSize = 24

    /** Вызывается в начале жеста за ручку масштабирования (правый нижний угол) */
    fun beginScaleGesture() {
        if (!_state.value.canEditProject) return
        val el = _state.value.selectedElement ?: return
        if (el.locked) return
        pushUndo()
        captureGesture(el)
        scaleStartW = el.w
        scaleStartH = el.h
        scaleStartFontSize = el.fontSize ?: 24
    }

    /**
     * Пропорциональное масштабирование (ручка в правом нижнем углу):
     * изображение и рамка растут с сохранением пропорций,
     * у текста дополнительно масштабируется размер шрифта.
     */
    fun scaleElement(id: String, newW: Float) {
        if (!canContinueGesture(id)) return
        val s = _state.value
        val el = s.elements.find { it.id == id } ?: return
        if (el.locked || scaleStartW <= 0f) return
        // Чувствительность ручки масштаба приглушена вдвое:
        // элемент меняет размер плавнее и предсказуемее
        val rawFactor = (newW / scaleStartW).coerceIn(0.05f, 50f)
        val factor = (1f + (rawFactor - 1f) * 0.5f).coerceIn(0.05f, 50f)
        val w = (scaleStartW * factor).coerceAtLeast(10f)
        val h = (scaleStartH * factor).coerceAtLeast(10f)
        _state.value = s.copy(
            elements = s.elements.map {
                if (it.id == id) {
                    if (it.isText) {
                        fitTextBox(
                            it.copy(
                                w = w,
                                h = h,
                                fontSize = (scaleStartFontSize * factor).toInt().coerceIn(4, 2000),
                            ),
                        )
                    } else {
                        // При включённой «Привязке к сетке» все края
                        // прилипают к ближайшим линиям во время масштабирования
                        val rect = snapRectToGrid(it.x, it.y, w, h)
                        it.copy(x = rect[0], y = rect[1], w = rect[2], h = rect[3])
                    }
                } else it
            },
        ).withProjectEdit()
    }

    // Угол элемента на старте жеста поворота (за кругляшок над рамкой)
    private var rotateStartAngle = 0f

    /** Вызывается в начале жеста за ручку поворота */
    fun beginRotateGesture() {
        if (!_state.value.canEditProject) return
        val el = _state.value.selectedElement ?: return
        if (el.locked) return
        pushUndo()
        captureGesture(el)
        rotateStartAngle = el.rotation
    }

    /**
     * Поворот элемента кругляшком на рамке: deltaDegrees — смещение
     * угла от начала жеста. С зажатой привязкой углы прилипают к 15°.
     */
    fun rotateElement(id: String, deltaDegrees: Float, snap: Boolean = false) {
        if (!canContinueGesture(id)) return
        val s = _state.value
        val el = s.elements.find { it.id == id } ?: return
        if (el.locked) return
        var angle = (rotateStartAngle + deltaDegrees) % 360f
        if (angle < 0f) angle += 360f
        if (snap) angle = (Math.round(angle / 15f) * 15f) % 360f
        _state.value = s.copy(
            elements = s.elements.map {
                if (it.id == id) it.copy(rotation = angle) else it
            },
        ).withProjectEdit()
    }

    /** Переименование элемента (слоя) */
    fun renameElement(id: String, newName: String) {
        if (newName.isBlank()) return
        updateElement(id, { it.copy(name = newName.trim()) })
    }

    /** Показ/скрытие размерных меток у конкретного элемента */
    fun toggleDimensions(id: String) {
        updateElement(id, { it.copy(showDimensions = !it.showDimensions) })
    }

    fun deleteElement(id: String) {
        if (!_state.value.canEditProject || _state.value.elements.none { it.id == id }) return
        pushUndo()
        val s = _state.value
        _state.value = s.copy(
            elements = s.elements.filter { it.id != id },
            selectedId = if (s.selectedId == id) null else s.selectedId,
        ).withProjectEdit()
        haptic(strong = true)
        showToast("Элемент удалён")
    }

    fun duplicateElement(id: String) {
        if (!_state.value.canEditProject) return
        val original = _state.value.elements.find { it.id == id } ?: return
        val counter = _state.value.nextElementCounter() ?: return
        pushUndo()
        val s = _state.value
        // Имя дубликата совпадает с оригиналом — без суффикса «_copy»
        val copy = original.copy(
            id = "el$counter",
            name = original.name,
            x = original.x + 30f,
            y = original.y + 30f,
            z = s.elements.size,
            locked = false,
        )
        _state.value = s.copy(
            elements = s.elements + copy,
            counter = counter,
            selectedId = copy.id,
        ).withProjectEdit()
        haptic(strong = true)
        showToast("Элемент дублирован")
    }

    fun toggleLock(id: String) {
        if (!_state.value.canEditProject) return
        updateElement(id, { it.copy(locked = !it.locked) })
        haptic()
    }

    fun moveLayer(id: String, up: Boolean) {
        if (!_state.value.canEditProject) return
        val sorted = _state.value.elements.sortedBy { it.z }.toMutableList()
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = if (up) index + 1 else index - 1
        if (target < 0 || target >= sorted.size) return
        pushUndo()
        val tmp = sorted[index]
        sorted[index] = sorted[target]
        sorted[target] = tmp
        _state.value = _state.value.copy(
            elements = sorted.mapIndexed { i, el -> el.copy(z = i) },
        ).withProjectEdit()
        haptic()
    }

    fun clearCanvas() {
        if (!_state.value.canEditProject || _state.value.elements.isEmpty()) return
        pushUndo()
        _state.value = _state.value.copy(
            elements = emptyList(),
            selectedId = null,
        ).withProjectEdit()
        haptic(strong = true)
        showToast("Холст очищен")
    }

    // ---------- Пользовательская палитра ----------

    /** Сохранение цвета в одну из 8 ячеек «Мои цвета» */
    fun saveCustomColor(index: Int, hex: String) {
        val token = _state.value.authoringToken() ?: return
        viewModelScope.launch {
            if (!_state.value.acceptsAuthoringResult(token)) return@launch
            settingsRepository.setCustomColor(index, hex)
        }
        haptic()
        showToast("Цвет сохранён в палитру")
    }

    /** Снимок текущего холста для пипетки (белая подложка вместо прозрачности) */
    fun canvasSnapshot(): android.graphics.Bitmap? {
        return try {
            com.fallen.studio.export.ImageExporter.render(
                project = currentProject(),
                scale = 0.5f,
                transparentBackground = false,
                context = getApplication(),
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------- Toast ----------

    fun showToast(message: String) {
        _state.value = _state.value.copy(toast = message)
    }

    fun consumeToast() {
        _state.value = _state.value.copy(toast = null)
    }
}
