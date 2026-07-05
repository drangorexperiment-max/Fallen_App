package com.fallen.studio.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fallen.studio.R
import com.fallen.studio.ui.theme.FallenTheme

/**
 * Лого Fallen — фирменный знак (изображение из ресурсов).
 *
 * Логотип — белый кот на прозрачном фоне, поэтому в СВЕТЛОЙ теме
 * он подкладывается на тёмный круг — иначе сливается с белым фоном.
 * В тёмной теме рисуется как есть.
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
    // Светлая тема определяется по светлоте фона приложения
    val isLightTheme = FallenTheme.colors.appBackground.luminance() > 0.5f
    val alphaMod = if (glowProgress > 0f) 0.75f + 0.25f * glowProgress else 1f

    if (isLightTheme) {
        // Тёмная круглая подложка, чтобы белый логотип был виден
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(Color(0xFF16161F))
                .alpha(alphaMod),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.fallen_logo),
                contentDescription = "Логотип Fallen",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
        }
    } else {
        Image(
            painter = painterResource(id = R.drawable.fallen_logo),
            contentDescription = "Логотип Fallen",
            modifier = modifier.alpha(alphaMod),
        )
    }
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
