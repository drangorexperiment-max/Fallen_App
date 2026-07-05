package com.fallen.studio.ui.screens.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fallen.studio.data.model.ProjectSummary
import com.fallen.studio.ui.components.FallenLogoMark
import com.fallen.studio.ui.theme.FallenTheme
import com.fallen.studio.util.ImageUtils

/**
 * Главный экран Fallen: список проектов, создание, импорт .uiproj,
 * настройки.
 */
@Composable
fun HomeScreen(
    onOpenProject: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateProject: (w: Int, h: Int) -> Unit = { _, _ -> onOpenProject(null) },
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = FallenTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    var newProjectOpen by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let(viewModel::importProject)
    }

    // Экспорт проекта в файл: запоминаем id, затем SAF-диалог выбора места
    var exportProjectId by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val id = exportProjectId
        exportProjectId = null
        if (uri != null && id != null) viewModel.exportProject(id, uri)
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    LaunchedEffect(state.openProjectId) {
        state.openProjectId?.let {
            viewModel.consumeOpenProject()
            onOpenProject(it)
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
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            // ---------- Шапка ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FallenLogoMark(size = 40.dp, glowing = true)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Fallen",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "Настройки",
                        tint = colors.textSecondary,
                    )
                }
            }

            // ---------- Кнопки действий ----------
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { newProjectOpen = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Новый проект", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                FilledTonalButton(
                    onClick = { importLauncher.launch("*/*") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Импорт", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Мои проекты",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))

            // ---------- Список проектов ----------
            if (state.projects.isEmpty() && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FallenLogoMark(size = 56.dp, glowing = false)
                    Text(
                        text = "Пока нет проектов",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Создайте новый проект или импортируйте\nфайл проекта (.uiproj)",
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project.id) },
                            onRename = { viewModel.renameProject(project.id, it) },
                            onDuplicate = { viewModel.duplicateProject(project.id) },
                            onDelete = { viewModel.deleteProject(project.id) },
                            onExport = {
                                exportProjectId = project.id
                                exportLauncher.launch("${project.name}.uiproj")
                            },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (newProjectOpen) {
        NewProjectDialog(
            onDismiss = { newProjectOpen = false },
            onCreate = { w, h ->
                newProjectOpen = false
                onCreateProject(w, h)
            },
        )
    }
}

// ==================================================================
// Диалог создания проекта: выбор разрешения холста
// ==================================================================

private val canvasPresets = listOf(
    Triple("Full HD", 1920, 1080),
    Triple("Full HD верт.", 1080, 1920),
    Triple("2K QHD", 2560, 1440),
    Triple("HD", 1280, 720),
    Triple("Квадрат", 1080, 1080),
    Triple("4K UHD", 3840, 2160),
)

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (w: Int, h: Int) -> Unit,
) {
    val colors = FallenTheme.colors
    var selectedPreset by remember { mutableStateOf(0) }
    var customMode by remember { mutableStateOf(false) }
    var customW by remember { mutableStateOf("1920") }
    var customH by remember { mutableStateOf("1080") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый проект") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Разрешение холста",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                )
                canvasPresets.forEachIndexed { index, (name, w, h) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (!customMode && selectedPreset == index)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else colors.surfaceElevated,
                            )
                            .border(
                                1.dp,
                                if (!customMode && selectedPreset == index)
                                    MaterialTheme.colorScheme.primary
                                else colors.divider,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable {
                                customMode = false
                                selectedPreset = index
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "$w × $h",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                        )
                    }
                }
                // Своё разрешение
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (customMode)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else colors.surfaceElevated,
                        )
                        .border(
                            1.dp,
                            if (customMode) MaterialTheme.colorScheme.primary else colors.divider,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { customMode = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Своё разрешение",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (customMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customW,
                            onValueChange = { customW = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Ширина") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = customH,
                            onValueChange = { customH = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Высота") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (customMode) {
                    val w = customW.toIntOrNull()?.coerceIn(100, 10000) ?: 1920
                    val h = customH.toIntOrNull()?.coerceIn(100, 10000) ?: 1080
                    onCreate(w, h)
                } else {
                    val (_, w, h) = canvasPresets[selectedPreset]
                    onCreate(w, h)
                }
            }) { Text("Создать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

// ==================================================================
// Карточка проекта
// ==================================================================

@Composable
private fun ProjectCard(
    project: ProjectSummary,
    onOpen: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val colors = FallenTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
    ) {
        // Миниатюра
        val thumb = remember(project.thumbnailSrc) {
            project.thumbnailSrc?.let { ImageUtils.decodeDataUrl(it) }
        }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .border(1.dp, colors.divider, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FallenLogoMark(size = 26.dp)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${project.canvasW} × ${project.canvasH}",
                fontSize = 12.sp,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = colors.textSecondary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = project.imageCount.toString(),
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.TextFields,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = colors.textSecondary,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = project.textCount.toString(),
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = "Действия",
                    tint = colors.textSecondary,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Переименовать") },
                    leadingIcon = {
                        Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                    },
                    onClick = {
                        menuOpen = false
                        renameOpen = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Дублировать") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDuplicate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Экспорт в файл") },
                    leadingIcon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onExport()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuOpen = false
                        deleteOpen = true
                    },
                )
            }
        }
    }

    if (renameOpen) {
        var name by remember { mutableStateOf(project.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Переименовать проект") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(name)
                    renameOpen = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Отмена") }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Удалить проект?") },
            text = { Text("«${project.name}» будет удалён безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    deleteOpen = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("Отмена") }
            },
        )
    }
}
