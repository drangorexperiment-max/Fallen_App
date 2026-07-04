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
    val canvasBackground: Color,
    val gridLine: Color,
    val selection: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

val LocalFallenColors = staticCompositionLocalOf {
    FallenColors(
        canvasBackground = CanvasDark,
        gridLine = GridLineDark,
        selection = SelectionColor,
        success = AccentSuccess,
        danger = AccentDanger,
        warning = AccentWarning,
        surfaceElevated = Dark3,
        borderSubtle = Dark4,
        borderStrong = Dark5,
        textSecondary = TextSecondaryDark,
        textTertiary = TextTertiaryDark,
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Violet500,
    onPrimary = Color.White,
    primaryContainer = Violet700,
    onPrimaryContainer = Color.White,
    secondary = Violet400,
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
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Violet300,
    onPrimaryContainer = Dark1,
    secondary = Violet500,
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

private val DarkFallenColors = FallenColors(
    canvasBackground = CanvasDark,
    gridLine = GridLineDark,
    selection = SelectionColor,
    success = AccentSuccess,
    danger = AccentDanger,
    warning = AccentWarning,
    surfaceElevated = Dark3,
    borderSubtle = Dark4,
    borderStrong = Dark5,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
)

private val LightFallenColors = FallenColors(
    canvasBackground = CanvasLight,
    gridLine = GridLineLight,
    selection = SelectionColor,
    success = AccentSuccess,
    danger = AccentDanger,
    warning = AccentWarning,
    surfaceElevated = Light2,
    borderSubtle = Light3,
    borderStrong = Light4,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
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

/** Быстрый доступ к расширенным цветам: `FallenTheme.colors.canvasBackground` */
object FallenThemeAccess {
    val colors: FallenColors
        @Composable get() = LocalFallenColors.current
}
