package com.fallen.studio.ui.screens.editor.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fallen.studio.ui.components.FallenColorPicker
import com.fallen.studio.ui.components.SliderRow

/**
 * Панель «Текст»: создание нового текстового элемента.
 */
@Composable
fun TextPanel(
    onAddText: (text: String, fontSize: Int, fontWeight: String, color: String, textAlign: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    var fontSize by remember { mutableFloatStateOf(48f) }
    var bold by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf("#FFFFFF") }
    var align by remember { mutableStateOf("center") }
    var showColorPicker by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Button(
            onClick = {
                onAddText(text, fontSize.toInt(), if (bold) "700" else "400", color, align)
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
