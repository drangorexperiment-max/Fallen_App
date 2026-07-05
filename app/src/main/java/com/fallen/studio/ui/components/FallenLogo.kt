package com.fallen.studio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.fallen.studio.R

/**
 * Лого Fallen — фирменный знак (изображение из ресурсов).
 *
 * @param glowProgress 0f..1f — прозрачность/пульсация (для анимации на сплэше)
 * @param backgroundColor не используется, оставлен для совместимости вызовов
 */
@Composable
fun FallenLogo(
    modifier: Modifier = Modifier,
    glowProgress: Float = 0f,
    backgroundColor: Color = Color.Unspecified,
) {
    Image(
        painter = painterResource(id = R.drawable.fallen_logo),
        contentDescription = "Логотип Fallen",
        modifier = modifier.alpha(if (glowProgress > 0f) 0.75f + 0.25f * glowProgress else 1f),
    )
}

/**
 * Компактный знак Fallen для шапок и списков.
 *
 * @param size размер знака
 * @param glowing если true — мягкая пульсация прозрачности
 */
@Composable
fun FallenLogoMark(
    size: Dp,
    modifier: Modifier = Modifier,
    glowing: Boolean = false,
) {
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
    )
}
