package com.fallen.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fallen.studio.util.ColorUtils

private val PresetColors = listOf(
    "#FFFFFF", "#E5E7EB", "#9CA3AF", "#4B5563", "#1F2937", "#000000",
    "#EF4444", "#F97316", "#F59E0B", "#EAB308", "#84CC16", "#22C55E",
    "#10B981", "#14B8A6", "#06B6D4", "#0EA5E9", "#3B82F6", "#6366F1",
    "#8B5CF6", "#A855F7", "#D946EF", "#EC4899", "#F43F5E", "#78350F"
)

/**
 * Полноценный выбор цвета: пресеты + HSV-градиент + HEX-ввод +
 * пипетка (выбор цвета с холста) + 8 пользовательских ячеек палитры.
 */
@Composable
fun FallenColorPicker(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 8 сохранённых пользователем цветов (hex или пустая строка) */
    customColors: List<String> = emptyList(),
    /** Сохранение цвета в ячейку (index 0..7, hex) */
    onSaveCustomColor: ((Int, String) -> Unit)? = null,
    /** Провайдер снимка холста для пипетки (null — пипетка скрыта) */
    eyedropperBitmap: (() -> android.graphics.Bitmap?)? = null,
) {
    var hexInput by remember(currentColor) { mutableStateOf(currentColor.removePrefix("#").uppercase()) }
    var hue by remember { mutableFloatStateOf(ColorUtils.hueOf(currentColor)) }
    var eyedropperOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Пресеты
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            userScrollEnabled = false
        ) {
            items(PresetColors) { hex ->
                ColorSwatch(
                    color = ColorUtils.parse(hex),
                    selected = currentColor.equals(hex, ignoreCase = true),
                    onClick = { onColorSelected(hex) },
                    size = 30.dp
                )
            }
        }

        // Насыщенность/яркость для выбранного оттенка
        SaturationValuePanel(
            hue = hue,
            onColorPicked = { c -> onColorSelected(ColorUtils.toHex(c)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        // Ползунок оттенка
        HueSlider(
            hue = hue,
            onHueChange = { hue = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        // ---------- Мои цвета: 8 ячеек ----------
        // Тап по заполненной ячейке — выбрать цвет.
        // Долгое нажатие по любой ячейке — сохранить текущий цвет в неё.
        if (onSaveCustomColor != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Мои цвета (долгое нажатие — сохранить)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(8) { i ->
                        val hex = customColors.getOrNull(i) ?: ""
                        val filled = hex.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    if (filled) ColorUtils.parse(hex)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape,
                                )
                                .border(
                                    width = if (filled && currentColor.equals(hex, true)) 2.dp else 1.dp,
                                    color = if (filled && currentColor.equals(hex, true))
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                )
                                .pointerInput(hex, currentColor) {
                                    detectTapGestures(
                                        onTap = { if (filled) onColorSelected(hex) },
                                        onLongPress = { onSaveCustomColor(i, currentColor) },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!filled) {
                                Text(
                                    text = "+",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        // HEX-ввод + пипетка
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(ColorUtils.parse(currentColor), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            )
            if (eyedropperBitmap != null) {
                IconButton(onClick = { eyedropperOpen = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Colorize,
                        contentDescription = "Пипетка: взять цвет с холста",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    val cleaned = raw.removePrefix("#").take(8).uppercase()
                    hexInput = cleaned
                    if (cleaned.length == 6 || cleaned.length == 8) {
                        ColorUtils.parseOrNull("#$cleaned")?.let {
                            onColorSelected("#$cleaned")
                        }
                    }
                },
                prefix = { Text("#") },
                singleLine = true,
                label = { Text("HEX") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    // ---------- Диалог пипетки ----------
    // Показывает снимок холста; тап по любой точке выбирает её цвет
    if (eyedropperOpen && eyedropperBitmap != null) {
        val bitmap = remember(eyedropperOpen) { eyedropperBitmap() }
        Dialog(onDismissRequest = { eyedropperOpen = false }) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Пипетка: коснитесь нужного цвета",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (bitmap != null) {
                    var imgSize by remember { mutableStateOf(IntSize(1, 1)) }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Снимок холста для выбора цвета",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { imgSize = it }
                            .pointerInput(bitmap) {
                                detectTapGestures { pos ->
                                    // Пересчёт координат тапа в пиксели bitmap
                                    // с учётом letterbox от ContentScale.Fit
                                    val scale = minOf(
                                        imgSize.width.toFloat() / bitmap.width,
                                        imgSize.height.toFloat() / bitmap.height,
                                    )
                                    val drawnW = bitmap.width * scale
                                    val drawnH = bitmap.height * scale
                                    val offX = (imgSize.width - drawnW) / 2f
                                    val offY = (imgSize.height - drawnH) / 2f
                                    val bx = ((pos.x - offX) / scale).toInt()
                                    val by = ((pos.y - offY) / scale).toInt()
                                    if (bx in 0 until bitmap.width && by in 0 until bitmap.height) {
                                        val px = bitmap.getPixel(bx, by)
                                        val hex = String.format(
                                            "#%02X%02X%02X",
                                            android.graphics.Color.red(px),
                                            android.graphics.Color.green(px),
                                            android.graphics.Color.blue(px),
                                        )
                                        onColorSelected(hex)
                                        eyedropperOpen = false
                                    }
                                }
                            },
                    )
                } else {
                    Text(
                        text = "Не удалось получить снимок холста",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(
                    onClick = { eyedropperOpen = false },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Отмена") }
            }
        }
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    onColorPicked: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var marker by remember { mutableStateOf<Offset?>(null) }
    var panelSize by remember { mutableStateOf(Offset(1f, 1f)) }

    fun pick(pos: Offset) {
        val x = pos.x.coerceIn(0f, panelSize.x)
        val y = pos.y.coerceIn(0f, panelSize.y)
        marker = Offset(x, y)
        val sat = x / panelSize.x
        val value = 1f - y / panelSize.y
        onColorPicked(Color.hsv(hue, sat, value))
    }

    Canvas(
        modifier = modifier
            .pointerInput(hue) {
                detectTapGestures { pos -> pick(pos) }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position)
                }
            }
    ) {
        panelSize = Offset(size.width, size.height)
        // Горизонтальный градиент: белый -> чистый оттенок
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.White, Color.hsv(hue, 1f, 1f))
            )
        )
        // Вертикальный градиент: прозрачный -> чёрный
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black)
            )
        )
        marker?.let { m ->
            drawCircle(Color.White, radius = 10f, center = m)
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = 11f, center = m, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    onHueChange((pos.x / width * 360f).coerceIn(0f, 360f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange((change.position.x / width * 360f).coerceIn(0f, 360f))
                }
            }
    ) {
        width = size.width
        val hueColors = List(37) { i -> Color.hsv(i * 10f, 1f, 1f) }
        drawRoundRect(
            brush = Brush.horizontalGradient(hueColors),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
        )
        val x = hue / 360f * size.width
        drawCircle(Color.White, radius = size.height / 2f - 2f, center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f))
        drawCircle(Color.hsv(hue, 1f, 1f), radius = size.height / 2f - 6f, center = Offset(x.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f))
    }
}
