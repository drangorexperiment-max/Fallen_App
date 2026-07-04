package com.fallen.studio.ui.screens.editor.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fallen.studio.data.model.CanvasElement
import com.fallen.studio.data.model.ProjectFont
import com.fallen.studio.ui.components.FallenColorPicker
import com.fallen.studio.ui.components.NumberField
import com.fallen.studio.ui.components.PanelSectionTitle
import com.fallen.studio.ui.components.SliderRow
import com.fallen.studio.ui.components.SwitchRow
import com.fallen.studio.util.FontManager

/**
 * Панель «Свойства»: редактирование выбранного элемента.
 * Для текста — полный набор: шрифт, обводка и тень
 * (тень корректно применяется и к обводке — см. TextElementRenderer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesPanel(
    element: CanvasElement?,
    onUpdate: ((CanvasElement) -> CanvasElement) -> Unit,
    modifier: Modifier = Modifier,
    fonts: List<ProjectFont> = emptyList(),
) {
    if (element == null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Ничего не выбрано",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Коснитесь элемента на холсте, чтобы изменить его свойства",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Свойства: ${element.name.ifBlank { "элемент" }}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ---------- Геометрия ----------
        PanelSectionTitle("Позиция и размер")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumberField(
                label = "X",
                value = element.x.toInt(),
                onValueChange = { v -> onUpdate { it.copy(x = v.toFloat()) } },
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Y",
                value = element.y.toInt(),
                onValueChange = { v -> onUpdate { it.copy(y = v.toFloat()) } },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NumberField(
                label = "Ширина",
                value = element.w.toInt(),
                onValueChange = { v -> onUpdate { it.copy(w = v.coerceAtLeast(10).toFloat()) } },
                min = 10,
                modifier = Modifier.weight(1f)
            )
            NumberField(
                label = "Высота",
                value = element.h.toInt(),
                onValueChange = { v -> onUpdate { it.copy(h = v.coerceAtLeast(10).toFloat()) } },
                min = 10,
                modifier = Modifier.weight(1f)
            )
        }

        SliderRow(
            label = "Прозрачность",
            value = element.opacity,
            onValueChange = { v -> onUpdate { it.copy(opacity = v) } },
            valueRange = 0f..100f,
            valueLabel = { "${it.toInt()}%" },
            modifier = Modifier.padding(top = 8.dp)
        )
        SliderRow(
            label = "Поворот",
            value = element.rotation,
            onValueChange = { v -> onUpdate { it.copy(rotation = v) } },
            valueRange = -180f..180f,
            valueLabel = { "${it.toInt()}°" }
        )

        // ---------- Текстовые свойства ----------
        if (element.isText) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            PanelSectionTitle("Текст")
            OutlinedTextField(
                value = element.text ?: "",
                onValueChange = { v -> onUpdate { it.copy(text = v) } },
                label = { Text("Содержимое") },
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            // Выбор шрифта: встроенные + загруженные в проект
            FontFamilySelector(
                current = element.fontFamily ?: "Inter",
                fonts = fonts,
                onSelect = { name -> onUpdate { it.copy(fontFamily = name) } },
            )

            SliderRow(
                label = "Размер шрифта",
                value = (element.fontSize ?: 48).toFloat(),
                onValueChange = { v -> onUpdate { it.copy(fontSize = v.toInt()) } },
                valueRange = 8f..300f,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isBold = element.fontWeight == "700" || element.fontWeight == "bold"
                FilterChip(
                    selected = isBold,
                    onClick = {
                        onUpdate { it.copy(fontWeight = if (isBold) "400" else "700") }
                    },
                    label = { Text("Жирный") },
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = element.textAlign == "left",
                        onClick = { onUpdate { it.copy(textAlign = "left") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Слева", modifier = Modifier.size(18.dp))
                    }
                    SegmentedButton(
                        selected = element.textAlign == "center" || element.textAlign == null,
                        onClick = { onUpdate { it.copy(textAlign = "center") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Icon(Icons.Outlined.FormatAlignCenter, contentDescription = "По центру", modifier = Modifier.size(18.dp))
                    }
                    SegmentedButton(
                        selected = element.textAlign == "right",
                        onClick = { onUpdate { it.copy(textAlign = "right") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.FormatAlignRight, contentDescription = "Справа", modifier = Modifier.size(18.dp))
                    }
                }
            }

            SliderRow(
                label = "Межстрочный интервал",
                value = element.lineHeight ?: 50f,
                onValueChange = { v -> onUpdate { it.copy(lineHeight = v) } },
                valueRange = 0f..100f
            )
            SliderRow(
                label = "Межбуквенный интервал",
                value = element.letterSpacing ?: 50f,
                onValueChange = { v -> onUpdate { it.copy(letterSpacing = v) } },
                valueRange = 0f..100f
            )

            // Цвет текста
            ColorRow(
                label = "Цвет текста",
                color = element.color ?: "#FFFFFF",
                onColorSelected = { c -> onUpdate { it.copy(color = c) } }
            )

            // ---------- Обводка ----------
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PanelSectionTitle("Обводка")
            SliderRow(
                label = "Толщина обводки",
                value = element.strokeWidth ?: 0f,
                onValueChange = { v -> onUpdate { it.copy(strokeWidth = v) } },
                valueRange = 0f..30f
            )
            if ((element.strokeWidth ?: 0f) > 0f) {
                ColorRow(
                    label = "Цвет обводки",
                    color = element.strokeColor ?: "#000000",
                    onColorSelected = { c -> onUpdate { it.copy(strokeColor = c) } }
                )
            }

            // ---------- Тень ----------
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            PanelSectionTitle("Тень")
            SwitchRow(
                label = "Включить тень",
                description = "Тень применяется к тексту вместе с обводкой",
                checked = element.shadowEnabled == true,
                onCheckedChange = { v -> onUpdate { it.copy(shadowEnabled = v) } }
            )
            if (element.shadowEnabled == true) {
                SliderRow(
                    label = "Размытие",
                    value = element.shadowBlur ?: 20f,
                    onValueChange = { v -> onUpdate { it.copy(shadowBlur = v) } },
                    valueRange = 0f..100f
                )
                SliderRow(
                    label = "Непрозрачность тени",
                    value = element.shadowOpacity ?: 50f,
                    onValueChange = { v -> onUpdate { it.copy(shadowOpacity = v) } },
                    valueRange = 0f..100f,
                    valueLabel = { "${it.toInt()}%" }
                )
                SliderRow(
                    label = "Смещение X",
                    value = element.shadowX ?: 50f,
                    onValueChange = { v -> onUpdate { it.copy(shadowX = v) } },
                    valueRange = 0f..100f,
                    valueLabel = { "${(it - 50f).toInt()}" }
                )
                SliderRow(
                    label = "Смещение Y",
                    value = element.shadowY ?: 50f,
                    onValueChange = { v -> onUpdate { it.copy(shadowY = v) } },
                    valueRange = 0f..100f,
                    valueLabel = { "${(it - 50f).toInt()}" }
                )
                ColorRow(
                    label = "Цвет тени",
                    color = element.shadowColor ?: "#000000",
                    onColorSelected = { c -> onUpdate { it.copy(shadowColor = c) } }
                )
            }
        }
    }
}

/** Выпадающий список шрифтов: встроенные + пользовательские из проекта */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FontFamilySelector(
    current: String,
    fonts: List<ProjectFont>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text("Шрифт") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            FontManager.builtinFontNames.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(name)
                        expanded = false
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
                        onClick = {
                            onSelect(font.name)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorRow(
    label: String,
    color: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть" else color)
            }
        }
        if (expanded) {
            FallenColorPicker(
                currentColor = color,
                onColorSelected = onColorSelected,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
