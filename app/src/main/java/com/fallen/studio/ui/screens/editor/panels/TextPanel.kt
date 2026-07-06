package com.fallen.studio.ui.screens.editor.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.ui.components.FallenColorPicker
import com.fallen.studio.ui.components.SliderRow
import com.fallen.studio.util.FontManager

/**
 * Панель «Текст»: создание нового текстового элемента
 * с выбором шрифта (встроенные + загруженные в проект).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextPanel(
    fonts: List<ProjectFont>,
    onAddText: (text: String, fontSize: Int, fontWeight: String, color: String, textAlign: String, fontFamily: String) -> Unit,
    onImportFont: () -> Unit,
    onDeleteFont: (String) -> Unit,
    modifier: Modifier = Modifier,
    customColors: List<String> = emptyList(),
    onSaveCustomColor: ((Int, String) -> Unit)? = null,
    eyedropperBitmap: (() -> android.graphics.Bitmap?)? = null,
) {
    var text by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(48f) }
    var bold by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf("#FFFFFF") }
    var align by remember { mutableStateOf("center") }
    var fontFamily by remember { mutableStateOf("Inter") }
    var showColorPicker by remember { mutableStateOf(false) }
    var fontMenuExpanded by remember { mutableStateOf(false) }

    // Если выбранный шрифт удалили из проекта — возвращаемся к Inter
    LaunchedEffect(fonts) {
        val known = FontManager.builtinFontNames + fonts.map { it.name }
        if (fontFamily !in known) fontFamily = "Inter"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Добавить текст",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст") },
            placeholder = { Text("Введите текст...") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // ---------- Выбор шрифта ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = fontMenuExpanded,
                onExpandedChange = { fontMenuExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = fontFamily,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Шрифт") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fontMenuExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = fontMenuExpanded,
                    onDismissRequest = { fontMenuExpanded = false }
                ) {
                    FontManager.builtinFontNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                fontFamily = name
                                fontMenuExpanded = false
                            }
                        )
                    }
                    if (fonts.isNotEmpty()) {
                        HorizontalDivider()
                        fonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font.name) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.FontDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        onDeleteFont(font.id)
                                        fontMenuExpanded = false
                                    }) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = "Удалить шрифт «${font.name}»",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    fontFamily = font.name
                                    fontMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            // Загрузка своего шрифта (.ttf / .otf)
            FilledTonalIconButton(
                onClick = onImportFont,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Загрузить шрифт (.ttf / .otf)")
            }
        }

        SliderRow(
            label = "Размер шрифта",
            value = fontSize,
            onValueChange = { fontSize = it },
            valueRange = 8f..300f,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Жирность и выравнивание
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = bold,
                onClick = { bold = !bold },
                label = { Text("Жирный") },
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = align == "left",
                    onClick = { align = "left" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Слева", modifier = Modifier.size(18.dp))
                }
                SegmentedButton(
                    selected = align == "center",
                    onClick = { align = "center" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Icon(Icons.Outlined.FormatAlignCenter, contentDescription = "По центру", modifier = Modifier.size(18.dp))
                }
                SegmentedButton(
                    selected = align == "right",
                    onClick = { align = "right" },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Icon(Icons.AutoMirrored.Outlined.FormatAlignRight, contentDescription = "Справа", modifier = Modifier.size(18.dp))
                }
            }
        }

        // Цвет
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Цвет текста",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = { showColorPicker = !showColorPicker }) {
                Text(if (showColorPicker) "Скрыть" else color)
            }
        }
        if (showColorPicker) {
            FallenColorPicker(
                currentColor = color,
                onColorSelected = { color = it },
                modifier = Modifier.padding(horizontal = 16.dp),
                customColors = customColors,
                onSaveCustomColor = onSaveCustomColor,
                eyedropperBitmap = eyedropperBitmap,
            )
        }

        Button(
            onClick = {
                onAddText(text, fontSize.toInt(), if (bold) "700" else "400", color, align, fontFamily)
                text = ""
            },
            enabled = text.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(48.dp)
        ) {
            Icon(Icons.Outlined.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Добавить на холст")
        }
    }
}
