package com.fallen.studio.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withResumed
import com.fallen.studio.ui.screens.editor.panels.AssetsPanel
import com.fallen.studio.ui.screens.editor.panels.ExportPanel
import com.fallen.studio.ui.screens.editor.panels.LayersPanel
import com.fallen.studio.ui.screens.editor.panels.NodesPanel
import com.fallen.studio.ui.screens.editor.panels.PropertiesPanel
import com.fallen.studio.ui.screens.editor.panels.TextPanel
import com.fallen.studio.ui.theme.FallenTheme
import kotlin.math.roundToInt

/**
 * Главный экран редактора Fallen.
 * Собирает: верхнюю панель (undo/redo/сохранение), канвас с сеткой,
 * нижнюю панель инструментов и выезжающие панели.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String?,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    initialCanvasW: Int = 1920,
    initialCanvasH: Int = 1080,
    viewModel: EditorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val colors = FallenTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnBack by rememberUpdatedState(onBack)
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionReady = state.isSessionReady && !state.isLoading && !state.isExitRequested
    val canEdit = sessionReady && !state.isPreview
    val canSave = canEdit && !state.saveInProgress
    val canStartPreview = canEdit && !state.saveInProgress &&
        !state.showSaveDialog && !state.showExitDialog && state.nodes.any { it.id == state.startNodeId }
    // Rendering is a projection only; every editing panel and export keeps authoring state.
    val canvasState = state.forCanvas()
    val displayedNode = if (state.isPreview) state.previewNode else state.activeNode
    val canvasSettings = if (state.isPreview) settings.copy(
        gridEnabled = false,
        showRulers = false,
        showCenterGuides = false,
        showSizeTooltip = false,
        canvasDimOutside = false,
    ) else settings
    val nodesVisible = !state.isPreview && state.activePanel == EditorPanel.NODES

    // Keep both launchers registered outside conditional panels; their pending tokens live in the VM.
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.completeAssetImport(uri)
    }
    // Лаунчер выбора файла шрифта (.ttf / .otf)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        viewModel.completeFontImport(uri)
    }
    var viewScale by remember { mutableFloatStateOf(0.15f) }
    // Счётчик нажатий кнопки «по центру» — триггер повторного центрирования
    var recenterTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showCanvasSizeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, projectId) {
        viewModel.loadProject(projectId, initialCanvasW, initialCanvasH)
    }

    LaunchedEffect(viewModel, lifecycleOwner, state.isExitRequested) {
        if (state.isExitRequested) {
            // popBackStackSafe принимает выход только из RESUMED; не теряем запрос
            // после recreation или во время навигационной анимации.
            lifecycleOwner.lifecycle.withResumed {
                if (viewModel.consumeExitRequest()) currentOnBack()
            }
        }
    }

    // Sheets/dialogs own their Back. Nodes return to the canvas; Preview walks its
    // history (or stops at the root). Only an authoring exit requests the dirty check.
    BackHandler(
        enabled = (state.activePanel == EditorPanel.NONE || nodesVisible || state.isPreview) &&
            !state.showSaveDialog && !state.showExitDialog &&
            !showClearDialog && !showCanvasSizeDialog &&
            state.loadError == null && state.saveError == null,
    ) {
        when {
            state.isPreview -> if (state.canPreviewBack) viewModel.previewBack() else viewModel.stopPreview()
            nodesVisible -> viewModel.closePanel()
            else -> viewModel.requestExit()
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    // Фон экрана совпадает с фоном области холста — под верхним и нижним
    // меню больше нет второго тёмно-серого слоя
    val editorBackground = if (isDarkTheme)
        androidx.compose.ui.graphics.Color(0xFF07070D)
    else
        androidx.compose.ui.graphics.Color(0xFFE4E4EC)

    Scaffold(
        containerColor = editorBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(editorBackground)
                .padding(padding)
                .statusBarsPadding(),
        ) {
            // ---------- Верхняя панель ----------
            EditorTopBar(
                canUndo = canEdit && state.canUndo,
                canRedo = canEdit && state.canRedo,
                isDirty = state.isDirty,
                canEdit = canEdit,
                canSave = canSave,
                projectName = state.projectName,
                nodeName = displayedNode?.name ?: "Экран",
                nodesVisible = nodesVisible,
                isPreview = state.isPreview,
                canPreview = canStartPreview,
                canPreviewBack = state.canPreviewBack,
                onNodes = {
                    if (state.isPreview) {
                        viewModel.stopPreview()
                        viewModel.openPanel(EditorPanel.NODES)
                    } else if (canEdit) viewModel.openPanel(EditorPanel.NODES)
                },
                onTogglePreview = {
                    if (state.isPreview) viewModel.stopPreview()
                    else if (canStartPreview) {
                        viewModel.closePanel()
                        viewModel.startPreview()
                    }
                },
                onPreviewBack = viewModel::previewBack,
                onBack = {
                    when {
                        state.isPreview -> viewModel.stopPreview()
                        nodesVisible -> viewModel.closePanel()
                        else -> viewModel.requestExit()
                    }
                },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = viewModel::openSaveDialog,
                onCanvasSize = { showCanvasSizeDialog = true },
                onClear = { showClearDialog = true },
                canvasLabel = "${canvasState.canvas.w} × ${canvasState.canvas.h}",
            )

            // ---------- Канвас ----------
            // clipToBounds не даёт увеличенному холсту перекрывать верхнее меню
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds(),
            ) {
                // Не разрешаем редактировать временный пустой state, пока идёт load.
                if (nodesVisible && canEdit) {
                    NodesPanel(
                        state = state,
                        onOpenNode = { id -> viewModel.selectNode(id); viewModel.closePanel() },
                        onReturnToCanvas = viewModel::closePanel,
                        onCreateNode = viewModel::createNode,
                        onRenameNode = viewModel::renameNode,
                        onDuplicateNode = viewModel::duplicateNode,
                        onDeleteNode = viewModel::deleteNode,
                        onSetStartNode = viewModel::setStartNode,
                        onSetElementTransition = viewModel::setElementTransition,
                        onSetElementBackAction = viewModel::setElementBackAction,
                        onClearElementTransition = viewModel::clearElementTransition,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (sessionReady) {
                    EditorCanvas(
                        state = canvasState,
                        settings = canvasSettings,
                        colors = colors,
                        isDarkTheme = isDarkTheme,
                        onSelect = viewModel::select,
                        onBeginGesture = viewModel::beginGesture,
                        onMove = viewModel::moveElement,
                        onResize = viewModel::resizeElement,
                        onBeginScaleGesture = viewModel::beginScaleGesture,
                        onScale = viewModel::scaleElement,
                        onBeginRotateGesture = viewModel::beginRotateGesture,
                        onRotate = { id, delta -> viewModel.rotateElement(id, delta) },
                        onViewTransform = { viewScale = it },
                        recenterTrigger = recenterTrigger,
                        previewMode = state.isPreview,
                        onActivateElement = viewModel::activatePreviewElement,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                // Индикатор масштаба + доступная 48dp кнопка центрирования.
                if (sessionReady && !nodesVisible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) {
                        IconButton(
                            onClick = { recenterTrigger++ },
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated.copy(alpha = 0.94f))
                                .border(1.dp, colors.divider, RoundedCornerShape(10.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CenterFocusWeak,
                                contentDescription = "Вписать холст в рабочую область",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Text(
                            text = "${(viewScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated.copy(alpha = 0.94f))
                                .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Preview never exposes authoring tools; stopping restores the active authoring node.
            if (state.isPreview) {
                Text(
                    "Preview: касайтесь элементов. Stop — вернуться в редактор.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                )
            } else {
                if (canEdit && state.selectedElement != null && !nodesVisible) {
                    TextButton(
                        onClick = { viewModel.openPanel(EditorPanel.NODES) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = "Настроить действие по нажатию для ${state.selectedElement?.name.orEmpty()}"
                            },
                    ) {
                        Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Действие выбранного элемента")
                    }
                }
                EditorBottomBar(
                    activePanel = state.activePanel,
                    assetsCount = state.assets.size,
                    layersCount = state.elements.size,
                    hasSelection = state.selectedElement != null,
                    onOpenPanel = { if (canEdit) viewModel.openPanel(it) },
                )
            }
        }
    }

    // ---------- Выезжающие панели ----------
    if (!state.isPreview && state.activePanel != EditorPanel.NONE && state.activePanel != EditorPanel.NODES) {
        // Без промежуточного состояния системный Back закрывает sheet за один шаг.
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePanel,
            sheetState = sheetState,
            containerColor = colors.surfaceElevated,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            when (state.activePanel) {
                EditorPanel.ASSETS -> AssetsPanel(
                    assets = state.assets,
                    onRequestAsset = {
                        if (viewModel.beginAssetImport()) {
                            try {
                                imagePickerLauncher.launch("image/*")
                            } catch (e: RuntimeException) {
                                viewModel.completeAssetImport(null)
                                throw e
                            }
                        }
                    },
                    onPlaceAsset = viewModel::placeAsset,
                    onRenameAsset = viewModel::renameAsset,
                    onDeleteAsset = viewModel::deleteAsset,
                )
                EditorPanel.TEXT -> TextPanel(
                    fonts = state.fonts,
                    onAddText = viewModel::addTextElement,
                    onImportFont = {
                        if (viewModel.beginFontImport()) {
                            try {
                                fontPickerLauncher.launch(
                                    arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"),
                                )
                            } catch (e: RuntimeException) {
                                viewModel.completeFontImport(null)
                                throw e
                            }
                        }
                    },
                    onDeleteFont = viewModel::deleteFont,
                    customColors = settings.customColors,
                    onSaveCustomColor = viewModel::saveCustomColor,
                    eyedropperBitmap = { viewModel.canvasSnapshot() },
                )
                EditorPanel.LAYERS -> LayersPanel(
                    elements = state.elements,
                    selectedId = state.selectedId,
                    onSelect = { viewModel.select(it) },
                    onToggleLock = viewModel::toggleLock,
                    onDuplicate = viewModel::duplicateElement,
                    onMoveLayer = viewModel::moveLayer,
                    onDelete = viewModel::deleteElement,
                    onRename = viewModel::renameElement,
                    onToggleDimensions = viewModel::toggleDimensions,
                    dimensionMarksEnabled = settings.showRulers,
                )
                EditorPanel.PROPERTIES -> PropertiesPanel(
                    element = state.selectedElement,
                    onUpdate = { transform ->
                        state.selectedId?.let { viewModel.updateElement(it, transform) }
                    },
                    fonts = state.fonts,
                    canvasW = state.canvas.w.toInt(),
                    canvasH = state.canvas.h.toInt(),
                    dimensionMarksEnabled = settings.showRulers,
                    customColors = settings.customColors,
                    onSaveCustomColor = viewModel::saveCustomColor,
                    eyedropperBitmap = { viewModel.canvasSnapshot() },
                )
                EditorPanel.EXPORT -> ExportPanel(
                    project = viewModel.currentProject(),
                    defaultFormat = settings.defaultExportFormat,
                    includeCommentsDefault = settings.includeComments,
                    onToast = viewModel::showToast,
                )
                EditorPanel.NODES -> Unit // Full, scrollable workspace above, not a constrained sheet.
                EditorPanel.NONE -> Unit
            }
        }
    }

    // ---------- Диалоги ----------
    state.loadError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::requestExit,
            title = { Text("Не удалось открыть проект") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::retryLoadProject, enabled = !state.isLoading) {
                    Text("Повторить")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::requestExit) { Text("Назад") }
            },
        )
    }

    if (state.showExitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExitDialog,
            title = { Text("Выйти из редактора?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (state.isDirty) "В проекте есть несохранённые изменения."
                        else "Текущие изменения уже сохранены.",
                    )
                    if (state.saveInProgress) Text("Дождитесь окончания сохранения.")
                    state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveAndExit, enabled = canSave) {
                    Text(if (state.saveInProgress) "Сохранение…" else "Сохранить и выйти")
                }
            },
            dismissButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = viewModel::discardAndExit, enabled = !state.saveInProgress) {
                        Text("Выйти без сохранения")
                    }
                    TextButton(onClick = viewModel::dismissExitDialog, enabled = !state.isManualSavePending) {
                        Text("Остаться")
                    }
                }
            },
        )
    }

    if (state.saveError != null && !state.showSaveDialog && !state.showExitDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveError,
            title = { Text("Не удалось сохранить проект") },
            text = { Text(state.saveError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.saveProject() }, enabled = canSave) { Text("Повторить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveError, enabled = !state.saveInProgress) {
                    Text("Закрыть")
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить холст?") },
            text = { Text("Все элементы будут удалены с холста. Действие можно отменить кнопкой «Откатить».") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCanvas()
                    showClearDialog = false
                }) { Text("Очистить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            },
        )
    }

    if (state.showSaveDialog) {
        var name by rememberSaveable(state.sessionId) { mutableStateOf(state.projectName) }
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveDialog,
            title = { Text("Сохранить проект") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название проекта") },
                        singleLine = true,
                        enabled = !state.isManualSavePending,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.saveProject(name.ifBlank { "Мой проект" }) },
                    enabled = canSave,
                ) { Text(if (state.saveInProgress) "Сохранение…" else "Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveDialog, enabled = !state.isManualSavePending) {
                    Text("Отмена")
                }
            },
        )
    }

    if (showCanvasSizeDialog) {
        var wText by remember { mutableStateOf(state.canvas.w.toString()) }
        var hText by remember { mutableStateOf(state.canvas.h.toString()) }
        AlertDialog(
            onDismissRequest = { showCanvasSizeDialog = false },
            title = { Text("Размер холста") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = wText,
                            onValueChange = { wText = it.filter { c -> c.isDigit() } },
                            label = { Text("Ширина") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = hText,
                            onValueChange = { hText = it.filter { c -> c.isDigit() } },
                            label = { Text("Высота") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Быстрые пресеты
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "2340×1080" to (2340 to 1080),
                            "1920×1080" to (1920 to 1080),
                            "1080×1920" to (1080 to 1920),
                        ).forEach { (label, size) ->
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        wText = size.first.toString()
                                        hText = size.second.toString()
                                    }
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = wText.toIntOrNull() ?: 0
                    val h = hText.toIntOrNull() ?: 0
                    viewModel.setCanvasSize(w, h)
                    showCanvasSizeDialog = false
                }) { Text("Применить") }
            },
            dismissButton = {
                TextButton(onClick = { showCanvasSizeDialog = false }) { Text("Отмена") }
            },
        )
    }
}

// ==================================================================
// Верхняя панель
// ==================================================================

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    isDirty: Boolean,
    canEdit: Boolean,
    canSave: Boolean,
    projectName: String,
    nodeName: String,
    nodesVisible: Boolean,
    isPreview: Boolean,
    canPreview: Boolean,
    canPreviewBack: Boolean,
    canvasLabel: String,
    onNodes: () -> Unit,
    onTogglePreview: () -> Unit,
    onPreviewBack: () -> Unit,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onCanvasSize: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = FallenTheme.colors
    var showIdentity by remember { mutableStateOf(false) }
    val identity: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = when {
                        isPreview -> "Остановить Preview и вернуться к редактированию"
                        nodesVisible -> "Вернуться на холст"
                        else -> "Выйти из редактора"
                    },
                    tint = colors.textSecondary,
                )
            }
            Column(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "Показать полные названия") { showIdentity = true }
                    .heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Проект $projectName. Нод $nodeName. Показать сведения" },
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    projectName.ifBlank { "Без имени" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    nodeName.ifBlank { "Без имени" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isDirty) {
                Box(
                    Modifier.padding(horizontal = 6.dp).size(7.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .semantics { contentDescription = "Есть несохранённые изменения" },
                )
            }
            Text(
                when {
                    isPreview -> "Preview"
                    nodesVisible -> "Ноды"
                    else -> "Редактор"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (isPreview) MaterialTheme.colorScheme.onPrimaryContainer else colors.textSecondary,
                modifier = Modifier.clip(RoundedCornerShape(7.dp))
                    .background(if (isPreview) MaterialTheme.colorScheme.primaryContainer else colors.divider.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
    val controls: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onNodes,
                enabled = canEdit || isPreview,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = if (isPreview) "Остановить Preview и открыть ноды проекта" else "Ноды проекта"
                },
            ) {
                Icon(Icons.Outlined.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Ноды")
            }
            Button(
                onClick = onTogglePreview,
                enabled = isPreview || canPreview,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = if (isPreview) "Stop: завершить Preview без изменения проекта"
                    else "Play: запустить Preview со стартового нода"
                },
            ) {
                Icon(
                    if (isPreview) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(if (isPreview) "Stop" else "Play")
            }
            if (isPreview) {
                TextButton(
                    onClick = onPreviewBack,
                    enabled = canPreviewBack,
                    modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = if (canPreviewBack) "Назад по истории Preview" else "История Preview пока пуста"
                    },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Назад")
                }
            } else {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = "Откатить",
                        tint = if (canUndo) MaterialTheme.colorScheme.onSurface else colors.textDisabled,
                    )
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Redo,
                        contentDescription = "Вернуть обратно",
                        tint = if (canRedo) MaterialTheme.colorScheme.onSurface else colors.textDisabled,
                    )
                }
                IconButton(onClick = onSave, enabled = canSave, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = "Сохранить проект",
                        tint = if (canSave) MaterialTheme.colorScheme.primary else colors.textDisabled,
                    )
                }
                TextButton(
                    onClick = onCanvasSize,
                    enabled = canEdit,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Размер текущего холста $canvasLabel" },
                ) { Text(canvasLabel, style = MaterialTheme.typography.labelMedium) }
                IconButton(onClick = onClear, enabled = canEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Очистить текущий холст", tint = colors.textSecondary)
                }
            }
        }
    }
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp)).padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        if (maxWidth >= 960.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { identity() }
                Box(Modifier.weight(2f)) { controls() }
            }
        } else {
            // On phones the same controls get a second, horizontally scrollable line.
            // Nothing is squeezed into tiny touch targets or silently clipped away.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                identity()
                controls()
            }
        }
    }
    if (showIdentity) {
        AlertDialog(
            onDismissRequest = { showIdentity = false },
            title = { Text(if (isPreview) "Экран в Preview" else "Текущий экран") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Проект", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(projectName.ifBlank { "Без имени" })
                    Text("Нод", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(nodeName.ifBlank { "Без имени" })
                    Text(canvasLabel)
                    if (isPreview) Text("Preview не изменяет проект. Stop возвращает к авторингу.")
                }
            },
            confirmButton = { TextButton(onClick = { showIdentity = false }) { Text("Закрыть") } },
        )
    }
}

// ==================================================================
// Нижняя панель инструментов
// ==================================================================

@Composable
private fun EditorBottomBar(
    activePanel: EditorPanel,
    assetsCount: Int,
    layersCount: Int,
    hasSelection: Boolean,
    onOpenPanel: (EditorPanel) -> Unit,
) {
    val colors = FallenTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(20.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarItem(
            icon = { Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = "Ассеты",
            badge = assetsCount,
            isActive = activePanel == EditorPanel.ASSETS,
            onClick = { onOpenPanel(EditorPanel.ASSETS) },
        )
        BottomBarItem(
            icon = { Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = "Текст",
            badge = null,
            isActive = activePanel == EditorPanel.TEXT,
            onClick = { onOpenPanel(EditorPanel.TEXT) },
        )
        BottomBarItem(
            icon = { Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = "Слои",
            badge = layersCount,
            isActive = activePanel == EditorPanel.LAYERS,
            onClick = { onOpenPanel(EditorPanel.LAYERS) },
        )
        BottomBarItem(
            icon = { Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = "Свойства",
            badge = null,
            isActive = activePanel == EditorPanel.PROPERTIES,
            highlight = hasSelection,
            onClick = { onOpenPanel(EditorPanel.PROPERTIES) },
        )
        BottomBarItem(
            icon = { Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = "Экспорт",
            badge = null,
            isActive = activePanel == EditorPanel.EXPORT,
            onClick = { onOpenPanel(EditorPanel.EXPORT) },
        )
    }
}

@Composable
private fun BottomBarItem(
    icon: @Composable () -> Unit,
    label: String,
    badge: Int?,
    isActive: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val colors = FallenTheme.colors
    val contentColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        highlight -> MaterialTheme.colorScheme.onSurface
        else -> colors.textSecondary
    }
    // ИСПРАВЛЕНИЕ: у контейнера иконки фиксированный размер, а бейдж
    // рисуется поверх через offset и НЕ участвует в раскладке — поэтому
    // иконка не съезжает и панель не увеличивается при добавлении элементов.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .sizeIn(minWidth = 64.dp, minHeight = 56.dp)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor,
            ) {
                icon()
            }
            if (badge != null && badge > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-6).dp),
                ) {
                    Text(text = badge.toString(), fontSize = 9.sp)
                }
            }
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
        )
    }
}
