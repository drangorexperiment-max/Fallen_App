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
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.util.FontManager
import com.fallen.studio.util.ImageUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Какая нижняя панель открыта */
enum class EditorPanel { NONE, ASSETS, TEXT, LAYERS, PROPERTIES, EXPORT }

/** Состояние редактора */
data class EditorState(
    val projectId: String? = null,
    val projectName: String = "Мой проект",
    val canvas: CanvasSize = CanvasSize(),
    val assets: List<Asset> = emptyList(),
    val elements: List<CanvasElement> = emptyList(),
    val fonts: List<ProjectFont> = emptyList(),
    val counter: Int = 0,
    val selectedId: String? = null,
    val activePanel: EditorPanel = EditorPanel.NONE,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isDirty: Boolean = false,
    val toast: String? = null,
) {
    val selectedElement: CanvasElement?
        get() = elements.find { it.id == selectedId }
}

/** Снимок для undo/redo */
private data class Snapshot(
    val canvas: CanvasSize,
    val assets: List<Asset>,
    val elements: List<CanvasElement>,
    val fonts: List<ProjectFont>,
    val counter: Int,
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(application)
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var autosaveJob: Job? = null

    // ---------- Загрузка / сохранение ----------

    fun loadProject(id: String?) {
        viewModelScope.launch {
            if (id == null) {
                val s = settings.value
                _state.value = EditorState(
                    canvas = CanvasSize(s.defaultCanvasW, s.defaultCanvasH),
                )
            } else {
                val project = repository.load(id) ?: return@launch
                applyProject(project)
            }
            undoStack.clear()
            redoStack.clear()
            updateUndoRedoFlags()
            startAutosave()
        }
    }

    fun importProject(project: FallenProject) {
        applyProject(project.copy(id = null))
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoFlags()
        showToast("Проект импортирован")
    }

    private fun applyProject(project: FallenProject) {
        _state.value = EditorState(
            projectId = project.id,
            projectName = project.name,
            canvas = project.canvas,
            assets = project.assets,
            elements = project.elements.sortedBy { it.z },
            fonts = project.fonts,
            counter = project.counter,
        )
    }

    fun currentProject(): FallenProject {
        val s = _state.value
        return FallenProject(
            id = s.projectId,
            name = s.projectName,
            canvas = s.canvas,
            assets = s.assets,
            elements = s.elements,
            fonts = s.fonts,
            counter = s.counter,
        )
    }

    fun saveProject(name: String? = null, onSaved: (String) -> Unit = {}) {
        viewModelScope.launch {
            val project = currentProject().let {
                if (name != null) it.copy(name = name) else it
            }
            val id = repository.save(project)
            _state.value = _state.value.copy(
                projectId = id,
                projectName = project.name,
                isDirty = false,
            )
            showToast("Проект сохранён")
            onSaved(id)
        }
    }

    private fun startAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            while (true) {
                val interval = settings.value.autosaveIntervalSec.coerceAtLeast(10)
                delay(interval * 1000L)
                if (settings.value.autosaveEnabled && _state.value.isDirty) {
                    repository.saveAutosave(currentProject())
                }
            }
        }
    }

    // ---------- Undo / Redo ----------

    private fun pushUndo() {
        val s = _state.value
        undoStack.addLast(Snapshot(s.canvas, s.assets, s.elements, s.fonts, s.counter))
        val limit = settings.value.undoLimit.coerceAtLeast(5)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        updateUndoRedoFlags()
    }

    fun undo() {
        val snapshot = undoStack.removeLastOrNull() ?: return
        val s = _state.value
        redoStack.addLast(Snapshot(s.canvas, s.assets, s.elements, s.fonts, s.counter))
        restore(snapshot)
        showToast("Отменено")
    }

    fun redo() {
        val snapshot = redoStack.removeLastOrNull() ?: return
        val s = _state.value
        undoStack.addLast(Snapshot(s.canvas, s.assets, s.elements, s.fonts, s.counter))
        restore(snapshot)
        showToast("Возвращено")
    }

    private fun restore(snapshot: Snapshot) {
        val selected = _state.value.selectedId
        _state.value = _state.value.copy(
            canvas = snapshot.canvas,
            assets = snapshot.assets,
            elements = snapshot.elements,
            fonts = snapshot.fonts,
            counter = snapshot.counter,
            selectedId = if (snapshot.elements.any { it.id == selected }) selected else null,
            isDirty = true,
        )
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
        if (w < 100 || h < 100 || w > 10000 || h > 10000) return
        pushUndo()
        _state.value = _state.value.copy(canvas = CanvasSize(w, h), isDirty = true)
    }

    // ---------- Выделение и панели ----------

    fun select(id: String?) {
        _state.value = _state.value.copy(selectedId = id)
    }

    fun openPanel(panel: EditorPanel) {
        _state.value = _state.value.copy(
            activePanel = if (_state.value.activePanel == panel) EditorPanel.NONE else panel,
        )
    }

    fun closePanel() {
        _state.value = _state.value.copy(activePanel = EditorPanel.NONE)
    }

    // ---------- Ассеты ----------

    fun addAssetFromUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val dataUrl = ImageUtils.uriToDataUrl(context, uri)
            if (dataUrl == null) {
                showToast("Не удалось загрузить изображение")
                return@launch
            }
            val name = ImageUtils.fileNameFromUri(context, uri)
            pushUndo()
            val asset = Asset(
                id = "a${System.currentTimeMillis()}_${(0..9999).random()}",
                name = name,
                src = dataUrl,
            )
            _state.value = _state.value.copy(
                assets = _state.value.assets + asset,
                isDirty = true,
            )
            showToast("Ассет «$name» добавлен")
        }
    }

    fun renameAsset(id: String, newName: String) {
        pushUndo()
        _state.value = _state.value.copy(
            assets = _state.value.assets.map {
                if (it.id == id) it.copy(name = newName) else it
            },
            elements = _state.value.elements.map {
                if (it.assetId == id) it.copy(name = newName) else it
            },
            isDirty = true,
        )
    }

    fun deleteAsset(id: String) {
        pushUndo()
        val s = _state.value
        val remainingElements = s.elements.filter { it.assetId != id }
        _state.value = s.copy(
            assets = s.assets.filter { it.id != id },
            elements = remainingElements,
            selectedId = if (remainingElements.any { it.id == s.selectedId }) s.selectedId else null,
            isDirty = true,
        )
        showToast("Ассет удалён")
    }

    /** Добавить экземпляр ассета на канвас */
    fun placeAsset(assetId: String) {
        val s = _state.value
        val asset = s.assets.find { it.id == assetId } ?: return
        pushUndo()
        val counter = s.counter + 1

        // Определяем реальные пропорции изображения
        val bitmap = ImageUtils.decodeDataUrl(asset.src)
        var w = 200f
        var h = 200f
        if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
            val aspect = bitmap.width.toFloat() / bitmap.height
            if (aspect >= 1f) h = w / aspect else w = h * aspect
        }

        val element = CanvasElement(
            id = "el$counter",
            type = "image",
            assetId = assetId,
            name = "${asset.name}_$counter",
            src = asset.src,
            x = s.canvas.w / 2f - w / 2f,
            y = s.canvas.h / 2f - h / 2f,
            w = w,
            h = h,
            z = s.elements.size,
        )
        _state.value = s.copy(
            elements = s.elements + element,
            counter = counter,
            selectedId = element.id,
            activePanel = EditorPanel.NONE,
            isDirty = true,
        )
        showToast("«${asset.name}» добавлен")
    }

    // ---------- Шрифты ----------

    /** Загружает пользовательский шрифт (.ttf / .otf) в проект */
    fun addFontFromUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val font = FontManager.fontFromUri(context, uri)
            if (font == null) {
                showToast("Не удалось загрузить шрифт (нужен .ttf или .otf до 8 МБ)")
                return@launch
            }
            if (_state.value.fonts.any { it.name == font.name }) {
                showToast("Шрифт «${font.name}» уже добавлен")
                return@launch
            }
            pushUndo()
            _state.value = _state.value.copy(
                fonts = _state.value.fonts + font,
                isDirty = true,
            )
            showToast("Шрифт «${font.name}» добавлен")
        }
    }

    /** Удаляет шрифт из проекта; элементы возвращаются на шрифт по умолчанию */
    fun deleteFont(id: String) {
        pushUndo()
        val s = _state.value
        val font = s.fonts.find { it.id == id } ?: return
        FontManager.invalidate(id)
        _state.value = s.copy(
            fonts = s.fonts.filter { it.id != id },
            elements = s.elements.map {
                if (it.fontFamily == font.name || it.fontFamily == font.id) {
                    it.copy(fontFamily = "Inter")
                } else it
            },
            isDirty = true,
        )
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
        if (text.isBlank()) {
            showToast("Введите текст")
            return
        }
        pushUndo()
        val s = _state.value
        val counter = s.counter + 1
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
        _state.value = s.copy(
            elements = s.elements + element,
            counter = counter,
            selectedId = element.id,
            activePanel = EditorPanel.NONE,
            isDirty = true,
        )
        showToast("Текст добавлен")
    }

    // ---------- Операции над элементами ----------

    fun updateElement(id: String, transform: (CanvasElement) -> CanvasElement, recordUndo: Boolean = true) {
        if (recordUndo) pushUndo()
        _state.value = _state.value.copy(
            elements = _state.value.elements.map {
                if (it.id == id) transform(it) else it
            },
            isDirty = true,
        )
    }

    // Сырая (не «примагниченная») позиция элемента во время жеста.
    // Snap применяется к ней, а не к уже квантованной позиции —
    // иначе элемент дрейфует и «прилипает» к границам сам по себе.
    private var gestureRawX = 0f
    private var gestureRawY = 0f
    private var gestureElementId: String? = null

    /** Для непрерывных жестов: undo пишется один раз в начале жеста */
    fun beginGesture() {
        pushUndo()
        val el = _state.value.selectedElement
        gestureElementId = el?.id
        gestureRawX = el?.x ?: 0f
        gestureRawY = el?.y ?: 0f
    }

    fun moveElement(id: String, dx: Float, dy: Float) {
        val s = _state.value
        val el = s.elements.find { it.id == id } ?: return
        if (el.locked) return

        // Если жест начался с другого элемента — переинициализируем raw-позицию
        if (gestureElementId != id) {
            gestureElementId = id
            gestureRawX = el.x
            gestureRawY = el.y
        }
        gestureRawX += dx
        gestureRawY += dy

        var nx = gestureRawX
        var ny = gestureRawY
        val cfg = settings.value

        // Магнитная привязка (как в оригинале): к центру/краям холста
        // и к границам других элементов. Работает от сырой позиции,
        // поэтому элемент легко «отлипает» при дальнейшем движении.
        if (cfg.snapEnabled) {
            val snapped = applySnap(nx, ny, el.w, el.h, id, s)
            nx = snapped.first
            ny = snapped.second
        }

        // Привязка к сетке: округление к ближайшему узлу (не усечение)
        if (cfg.snapToGrid) {
            val grid = cfg.gridSize.coerceAtLeast(2)
            nx = Math.round(nx / grid) * grid.toFloat()
            ny = Math.round(ny / grid) * grid.toFloat()
        }

        _state.value = s.copy(
            elements = s.elements.map {
                if (it.id == id) it.copy(x = nx, y = ny) else it
            },
            isDirty = true,
        )
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
        _state.value = _state.value.copy(
            elements = _state.value.elements.map { el ->
                if (el.id == id && !el.locked) {
                    el.copy(
                        x = newX,
                        y = newY,
                        w = newW.coerceAtLeast(10f),
                        h = newH.coerceAtLeast(10f),
                    )
                } else el
            },
            isDirty = true,
        )
    }

    fun deleteElement(id: String) {
        pushUndo()
        val s = _state.value
        _state.value = s.copy(
            elements = s.elements.filter { it.id != id },
            selectedId = if (s.selectedId == id) null else s.selectedId,
            isDirty = true,
        )
        showToast("Элемент удалён")
    }

    fun duplicateElement(id: String) {
        val s = _state.value
        val original = s.elements.find { it.id == id } ?: return
        pushUndo()
        val counter = s.counter + 1
        val copy = original.copy(
            id = "el$counter",
            name = original.name + "_copy",
            x = original.x + 30f,
            y = original.y + 30f,
            z = s.elements.size,
            locked = false,
        )
        _state.value = s.copy(
            elements = s.elements + copy,
            counter = counter,
            selectedId = copy.id,
            isDirty = true,
        )
        showToast("Элемент дублирован")
    }

    fun toggleLock(id: String) {
        updateElement(id, { it.copy(locked = !it.locked) })
    }

    fun moveLayer(id: String, up: Boolean) {
        pushUndo()
        val sorted = _state.value.elements.sortedBy { it.z }.toMutableList()
        val index = sorted.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = if (up) index + 1 else index - 1
        if (target < 0 || target >= sorted.size) return
        val tmp = sorted[index]
        sorted[index] = sorted[target]
        sorted[target] = tmp
        _state.value = _state.value.copy(
            elements = sorted.mapIndexed { i, el -> el.copy(z = i) },
            isDirty = true,
        )
    }

    fun clearCanvas() {
        pushUndo()
        _state.value = _state.value.copy(
            elements = emptyList(),
            selectedId = null,
            isDirty = true,
        )
        showToast("Холст очищен")
    }

    // ---------- Toast ----------

    fun showToast(message: String) {
        _state.value = _state.value.copy(toast = message)
    }

    fun consumeToast() {
        _state.value = _state.value.copy(toast = null)
    }
}
