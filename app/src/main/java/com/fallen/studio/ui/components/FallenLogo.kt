package com.fallen.studio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.fallen.studio.ui.theme.Violet400
import com.fallen.studio.ui.theme.Violet500
import com.fallen.studio.ui.theme.Violet700

/**
 * Лого Fallen — перевёрнутый треугольник с внутренним вырезом.
 *
 * @param glowProgress 0f..1f — интенсивность свечения (для анимации на сплэше)
 * @param backgroundColor цвет внутреннего выреза (обычно цвет фона)
 */
@Composable
fun FallenLogo(
    modifier: Modifier = Modifier,
    glowProgress: Float = 0f,
    backgroundColor: Color = Color(0xFF0A0A12),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Точки внешнего треугольника (вершина вниз)
        val top = h * 0.18f
        val bottom = h * 0.86f
        val left = w * 0.10f
        val right = w * 0.90f
        val centerX = w / 2f

        val outerPath = Path().apply {
            moveTo(centerX, bottom)
            lineTo(left, top)
            lineTo(right, top)
            close()
        }

        // Свечение (несколько полупрозрачных обводок вокруг)
        if (glowProgress > 0f) {
            val glowAlpha = 0.35f * glowProgress
            for (i in 3 downTo 1) {
                drawPath(
                    path = outerPath,
                    color = Violet400.copy(alpha = glowAlpha / i),
                    style = Stroke(
                        width = w * 0.045f * i * glowProgress,
                        pathEffect = PathEffect.cornerPathEffect(w * 0.02f),
                    ),
                )
            }
        }

        // Основной треугольник с градиентом
        drawPath(
            path = outerPath,
            brush = Brush.linearGradient(
                colors = listOf(Violet500, Violet700),
                start = Offset(left, top),
                end = Offset(right, bottom),
            ),
        )

        // Внутренний вырез
        val innerTop = h * 0.34f
        val innerBottom = h * 0.62f
        val innerLeft = w * 0.32f
        val innerRight = w * 0.68f

        val innerPath = Path().apply {
            moveTo(centerX, innerBottom)
            lineTo(innerLeft, innerTop)
            lineTo(innerRight, innerTop)
            close()
        }
        drawPath(path = innerPath, color = backgroundColor)
    }
}

/**
 * Компактный знак Fallen для шапок и списков.
 *
 * @param size размер знака
 * @param glowing если true — мягкая пульсирующая подсветка
 */
@Composable
fun FallenLogoMark(
    size: Dp,
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
) {
    val background = MaterialTheme.colorScheme.background
    val glow: Float = if (glowing) {
        val infinite = rememberInfiniteTransition(label = "markGlow")
        val value by infinite.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "markGlowPulse",
        )
        value
    } else {
        0f
    }

    FallenLogo(
        modifier = modifier.size(size),
        glowProgress = glow,
        backgroundColor = background,
    )
}
