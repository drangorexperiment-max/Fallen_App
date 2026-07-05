package com.fallen.studio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Дополнительные цвета Fallen, которых нет в Material3 ColorScheme.
 */
data class FallenColors(
    val appBackground: Color,
    val canvasBackground: Color,
    val gridLine: Color,
    val selection: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val surfaceElevated: Color,
    val divider: Color,
    val borderStrong: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
)

private val DarkFallenColors = FallenColors(
    appBackground = Dark1,
    canvasBackground = CanvasDark,
    gridLine = GridLineDark,
    selection = SelectionColor,
    success = AccentSuccess,
    danger = AccentDanger,
    warning = AccentWarning,
    surfaceElevated = Dark3,
    divider = Dark4,
    borderStrong = Dark5,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    textDisabled = TextTertiaryDark.copy(alpha = 0.6f),
)

private val LightFallenColors = FallenColors(
    appBackground = Light0,
    canvasBackground = CanvasLight,
    gridLine = GridLineLight,
    selection = SelectionColor,
    success = AccentSuccess,
    danger = AccentDanger,
    warning = AccentWarning,
    surfaceElevated = Light1,
    divider = Light3,
    borderStrong = Light4,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    textDisabled = TextTertiaryLight.copy(alpha = 0.6f),
)

val LocalFallenColors = staticCompositionLocalOf { DarkFallenColors }

private val DarkColorScheme = darkColorScheme(
    primary = Violet400,
    onPrimary = Dark1,
    primaryContainer = Violet700,
    onPrimaryContainer = Color.White,
    secondary = Violet500,
    onSecondary = Dark1,
    background = Dark1,
    onBackground = TextPrimaryDark,
    surface = Dark2,
    onSurface = TextPrimaryDark,
    surfaceVariant = Dark3,
    onSurfaceVariant = TextSecondaryDark,
    outline = Dark4,
    outlineVariant = Dark5,
    error = AccentDanger,
    onError = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Violet700,
    onPrimary = Color.White,
    primaryContainer = Violet300,
    onPrimaryContainer = Dark1,
    secondary = Violet600,
    onSecondary = Color.White,
    background = Light0,
    onBackground = TextPrimaryLight,
    surface = Light1,
    onSurface = TextPrimaryLight,
    surfaceVariant = Light2,
    onSurfaceVariant = TextSecondaryLight,
    outline = Light3,
    outlineVariant = Light4,
    error = AccentDanger,
    onError = Color.White,
)

@Composable
fun FallenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val fallenColors = if (darkTheme) DarkFallenColors else LightFallenColors

    CompositionLocalProvider(LocalFallenColors provides fallenColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FallenTypography,
            content = content,
        )
    }
}

/**
 * Быстрый доступ к расширенным цветам: `FallenTheme.colors.canvasBackground`.
 * Объект и composable-функция с одним именем сосуществуют в Kotlin,
 * так как находятся в разных пространствах имён.
 */
object FallenTheme {
    val colors: FallenColors
        @Composable get() = LocalFallenColors.current
}
