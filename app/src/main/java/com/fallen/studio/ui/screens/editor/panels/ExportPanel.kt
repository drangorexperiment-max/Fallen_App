package com.fallen.studio.ui.screens.editor.panels

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fallen.studio.data.ProjectSerializer
import com.fallen.studio.data.model.FallenProject
import com.fallen.studio.export.ExportEngine
import com.fallen.studio.export.ExportFormat
import com.fallen.studio.export.ImageExporter
import com.fallen.studio.ui.components.PanelSectionTitle
import com.fallen.studio.ui.components.SwitchRow

/**
 * Панель «Экспорт»: разнообразные форматы (пункт 9 требований).
 * - Код расстановки: Unity C#, JSON, TXT, CSV, XML
 * - Сохранение в файл через SAF, копирование, шаринг
 * - Экспорт холста в PNG
 */
@Composable
fun ExportPanel(
    project: FallenProject,
    defaultFormat: String,
    includeCommentsDefault: Boolean,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var format by remember {
        mutableStateOf(ExportFormat.entries.find { it.id == defaultFormat } ?: ExportFormat.UNITY)
    }
    var includeComments by remember { mutableStateOf(includeCommentsDefault) }
    var showPreview by remember { mutableStateOf(false) }
    var imageScale by remember { mutableStateOf(1f) }
    var transparentBg by remember { mutableStateOf(true) }
    var dimensionLabels by remember { mutableStateOf(false) }

    val generatedCode = remember(project, format, includeComments) {
        ExportEngine.generate(project, format, includeComments)
    }

    // SAF: сохранение кода в файл
    val saveCodeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(generatedCode.toByteArray())
                }
                onToast("Файл сохранён")
            }.onFailure { onToast("Не удалось сохранить файл") }
        }
    }

    // SAF: сохранение PNG
    val savePngLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            val ok = ImageExporter.exportToUri(context, project, uri, imageScale, transparentBg, dimensionLabels)
            onToast(if (ok) "PNG сохранён" else "Ошибка экспорта PNG")
        }
    }

    // SAF: экспорт файла проекта (.fallen) для передачи другим людям
    val saveProjectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(ProjectSerializer.encode(project).toByteArray())
                }
                onToast("Файл проекта сохранён")
            }.onFailure { onToast("Не удалось сохранить файл проекта") }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Экспорт",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ---------- Формат ----------
        PanelSectionTitle("Формат расстановки")
        ExportFormat.entries.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { format = f }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(selected = format == f, onClick = { format = f })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = f.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = f.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SwitchRow(
            label = "Комментарии в коде",
            checked = includeComments,
            onCheckedChange = { includeComments = it }
        )

        // ---------- Предпросмотр ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { showPreview = !showPreview },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (showPreview) "Скрыть код" else "Показать код")
            }
            FilledTonalButton(
                onClick = {
                    clipboard.setText(AnnotatedString(generatedCode))
                    onToast("Код скопирован")
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Копировать")
            }
        }

        if (showPreview) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = generatedCode,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // ---------- Сохранение и шаринг ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val fileName = "${project.name.ifBlank { "layout" }}.${format.fileExtension}"
                    saveCodeLauncher.launch(fileName)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("В файл")
            }
            FilledTonalButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, generatedCode)
                        putExtra(Intent.EXTRA_SUBJECT, project.name)
                    }
                    context.startActivity(Intent.createChooser(intent, "Поделиться кодом"))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Поделиться")
            }
        }

        // ---------- PNG ----------
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        PanelSectionTitle("Экспорт изображения (PNG)")

        // Выбор разрешения: список готовых вариантов на основе холста
        // + возможность вписать своё. Масштаб вычисляется автоматически.
        val canvasW = project.canvas.w.toInt()
        val canvasH = project.canvas.h.toInt()
        val presets = remember(canvasW, canvasH) {
            listOf(0.5f, 1f, 1.5f, 2f).map { s ->
                Triple((canvasW * s).toInt(), (canvasH * s).toInt(), s)
            }.filter { it.first in 1..8192 && it.second in 1..8192 }
        }
        var customMode by remember { mutableStateOf(false) }
        var customW by remember { mutableStateOf(canvasW.toString()) }

        presets.forEach { (w, h, s) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        customMode = false
                        imageScale = s
                    }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(
                    selected = !customMode && imageScale == s,
                    onClick = {
                        customMode = false
                        imageScale = s
                    },
                )
                Text(
                    text = "$w × $h" + if (s == 1f) " (оригинал)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Своё разрешение: ширина вводится цифрами, высота считается
        // по пропорциям холста
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { customMode = true }
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = customMode, onClick = { customMode = true })
            Text(
                text = "Своё разрешение",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (customMode) {
            val customWInt = customW.toIntOrNull()
            val customScale = if (customWInt != null && customWInt in 1..8192) {
                customWInt.toFloat() / canvasW
            } else null
            val customHInt = customScale?.let { (canvasH * it).toInt() }
            // Применяем введённое значение к масштабу экспорта
            if (customScale != null) imageScale = customScale

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customW,
                    onValueChange = { input ->
                        customW = input.filter { ch -> ch.isDigit() }.take(4)
                    },
                    label = { Text("Ширина, px") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (customHInt != null) "× $customHInt px" else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (customScale == null) {
                Text(
                    text = "Введите ширину от 1 до 8192",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        SwitchRow(
            label = "Прозрачный фон",
            checked = transparentBg,
            onCheckedChange = { transparentBg = it }
        )
        SwitchRow(
            label = "Размерные метки",
            description = "Стрелки с разрешением сторон холста и элементов внутри изображения",
            checked = dimensionLabels,
            onCheckedChange = { dimensionLabels = it }
        )
        Button(
            onClick = {
                savePngLauncher.launch("${project.name.ifBlank { "canvas" }}.png")
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(46.dp)
        ) {
            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Сохранить PNG")
        }

        // ---------- Файл проекта ----------
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        PanelSectionTitle("Файл проекта")
        Text(
            text = "Сохраните проект в файл .fallen, чтобы хранить его на вашем устройстве, а также отправить другому пользователю.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        FilledTonalButton(
            onClick = {
                saveProjectLauncher.launch("${project.name.ifBlank { "project" }}.fallen")
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(46.dp)
        ) {
            Icon(Icons.Outlined.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Экспорт проекта (.fallen)")
        }
    }
}
