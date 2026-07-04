package com.fallen.studio.ui.screens.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fallen.studio.ui.components.FallenLogoMark
import com.fallen.studio.ui.screens.editor.panels.AssetsPanel
import com.fallen.studio.ui.screens.editor.panels.ExportPanel
import com.fallen.studio.ui.screens.editor.panels.LayersPanel
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
    viewModel: EditorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val colors = FallenTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }

    // Лаунчер выбора файла шрифта (.ttf / .otf)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.addFontFromUri(it) }
    }
    var viewScale by remember { mutableFloatStateOf(0.15f) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showCanvasSizeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    Scaffold(
        containerColor = colors.appBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .padding(padding)
                .statusBarsPadding(),
        ) {
            // ---------- Верхняя панель ----------
            EditorTopBar(
                projectName = state.projectName,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                isDirty = state.isDirty,
                onBack = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onSave = { showSaveDialog = true },
                onCanvasSize = { showCanvasSizeDialog = true },
                onClear = { showClearDialog = true },
                canvasLabel = "${state.canvas.w} × ${state.canvas.h}",
            )

            // ---------- Канвас ----------
            Box(modifier = Modifier.weight(1f)) {
                EditorCanvas(
                    state = state,
                    settings = settings,
                    colors = colors,
                    isDarkTheme = isDarkTheme,
                    onSelect = viewModel::select,
                    onBeginGesture = viewModel::beginGesture,
                    onMove = viewModel::moveElement,
                    onResize = viewModel::resizeElement,
                    onViewTransform = { viewScale = it },
                    modifier = Modifier.fillMaxSize(),
                )

                // Индикатор масштаба
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated.copy(alpha = 0.9f))
                        .border(1.dp, colors.divider, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${(viewScale * 100).roundToInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                    )
                }
            }

            // ---------- Нижняя панель инструментов ----------
            EditorBottomBar(
                activePanel = state.activePanel,
                assetsCount = state.assets.size,
                layersCount = state.elements.size,
                hasSelection = state.selectedElement != null,
                onOpenPanel = viewModel::openPanel,
            )
        }
    }

    // ---------- Выезжающие панели ----------
    if (state.activePanel != EditorPanel.NONE) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePanel,
            sheetState = sheetState,
            containerColor = colors.surfaceElevated,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            when (state.activePanel) {
                EditorPanel.ASSETS -> AssetsPanel(
                    assets = state.assets,
                    onAddAsset = viewModel::addAssetFromUri,
                    onPlaceAsset = viewModel::placeAsset,
                    onRenameAsset = viewModel::renameAsset,
                    onDeleteAsset = viewModel::deleteAsset,
                )
                EditorPanel.TEXT -> TextPanel(
                    fonts = state.fonts,
                    onAddText = viewModel::addTextElement,
                    onImportFont = {
                        fontPickerLauncher.launch(
                            arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"),
                        )
                    },
                    onDeleteFont = viewModel::deleteFont,
                )
                EditorPanel.LAYERS -> LayersPanel(
                    elements = state.elements,
                    selectedId = state.selectedId,
                    onSelect = { viewModel.select(it) },
                    onToggleLock = viewModel::toggleLock,
                    onDuplicate = viewModel::duplicateElement,
                    onMoveLayer = viewModel::moveLayer,
                    onDelete = viewModel::deleteElement,
                )
                EditorPanel.PROPERTIES -> PropertiesPanel(
                    element = state.selectedElement,
                    onUpdate = { transform ->
                        state.selectedId?.let { viewModel.updateElement(it, transform) }
                    },
                    fonts = state.fonts,
                )
                EditorPanel.EXPORT -> ExportPanel(
                    project = viewModel.currentProject(),
                    defaultFormat = settings.defaultExportFormat,
                    includeCommentsDefault = settings.includeComments,
                    onToast = viewModel::showToast,
                )
                EditorPanel.NONE -> Unit
            }
        }
    }

    // ---------- Диалоги ----------
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

    if (showSaveDialog) {
        var name by remember { mutableStateOf(state.projectName) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Сохранить проект") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название проекта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveProject(name.ifBlank { "Мой проект" })
                    showSaveDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Отмена") }
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
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    isDirty: Boolean,
    canvasLabel: String,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onCanvasSize: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = FallenTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Назад",
                    tint = colors.textSecondary,
                )
            }
            FallenLogoMark(size = 22.dp)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = projectName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isDirty) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Text(
                    text = canvasLabel,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCanvasSize)
                        .padding(vertical = 1.dp),
                )
            }
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = "Откатить",
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else colors.textDisabled,
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Outlined.Redo,
                    contentDescription = "Вернуть обратно",
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface else colors.textDisabled,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = "Очистить холст",
                    tint = colors.textSecondary,
                )
            }
            IconButton(onClick = onSave) {
                Icon(
                    Icons.Outlined.Save,
                    contentDescription = "Сохранить проект",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(
                if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else androidx.compose.ui.graphics.Color.Transparent,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Box {
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
                        .padding(start = 14.dp, bottom = 12.dp),
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
