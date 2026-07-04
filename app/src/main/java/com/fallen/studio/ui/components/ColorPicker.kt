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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fallen.studio.util.ColorUtils

private val PresetColors = listOf(
    "#FFFFFF", "#E5E7EB", "#9CA3AF", "#4B5563", "#1F2937", "#000000",
    "#EF4444", "#F97316", "#F59E0B", "#EAB308", "#84CC16", "#22C55E",
    "#10B981", "#14B8A6", "#06B6D4", "#0EA5E9", "#3B82F6", "#6366F1",
    "#8B5CF6", "#A855F7", "#D946EF", "#EC4899", "#F43F5E", "#78350F"
)

/**
 * Полноценный выбор цвета: пресеты + HSV-градиент + HEX-ввод.
 */
@Composable
fun FallenColorPicker(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var hexInput by remember(currentColor) { mutableStateOf(currentColor.removePrefix("#").uppercase()) }
    var hue by remember { mutableFloatStateOf(ColorUtils.hueOf(currentColor)) }

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

        // HEX-ввод
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
