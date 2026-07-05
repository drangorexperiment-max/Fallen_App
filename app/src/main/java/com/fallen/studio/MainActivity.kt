package com.fallen.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fallen.studio.data.AppSettings
import com.fallen.studio.data.SettingsRepository
import com.fallen.studio.data.ThemeMode
import com.fallen.studio.ui.screens.editor.EditorScreen
import com.fallen.studio.ui.screens.home.HomeScreen
import com.fallen.studio.ui.screens.settings.SettingsScreen
import com.fallen.studio.ui.screens.splash.SplashScreen
import com.fallen.studio.ui.theme.FallenTheme

/**
 * Безопасная навигация: выполняется только когда текущий экран RESUMED.
 * Это устраняет «анимация иногда не срабатывает» — быстрые повторные тапы
 * во время перехода раньше ставили в стек дубликат экрана без анимации.
 */
private fun NavHostController.navigateSafe(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        navigate(route) { launchSingleTop = true }
    }
}

private fun NavHostController.popBackStackSafe() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        popBackStack()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FallenApp()
        }
    }
}

@Composable
private fun FallenApp() {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())

    val isDark = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    FallenTheme(darkTheme = isDark) {
        val navController = rememberNavController()

        // Плавное «перелистывание»: новый экран въезжает справа поверх,
        // старый слегка уезжает с параллаксом и притемняется.
        // 320 мс + Emphasized-кривая Material 3 — визуально плавно,
        // без ощущения «низкого fps» от слишком короткого тайминга.
        val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val duration = 320
        NavHost(
            navController = navController,
            startDestination = "splash",
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(duration, easing = easing),
                ) + fadeIn(animationSpec = tween(duration / 2))
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(duration, easing = easing),
                    targetOffset = { it / 3 }, // параллакс: уезжает на треть
                ) + fadeOut(animationSpec = tween(duration, easing = easing), targetAlpha = 0.6f)
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(duration, easing = easing),
                    initialOffset = { it / 3 },
                ) + fadeIn(animationSpec = tween(duration / 2), initialAlpha = 0.6f)
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(duration, easing = easing),
                ) + fadeOut(animationSpec = tween(duration / 2))
            },
        ) {
            composable("splash") {
                SplashScreen(
                    onFinished = {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    },
                )
            }

            composable("home") {
                HomeScreen(
                    onOpenProject = { projectId ->
                        val route = if (projectId == null) "editor" else "editor?projectId=$projectId"
                        navController.navigateSafe(route)
                    },
                    onCreateProject = { w, h ->
                        navController.navigateSafe("editor?canvasW=$w&canvasH=$h")
                    },
                    onOpenSettings = { navController.navigateSafe("settings") },
                )
            }

            composable(
                route = "editor?projectId={projectId}&canvasW={canvasW}&canvasH={canvasH}",
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("canvasW") {
                        type = NavType.IntType
                        defaultValue = 1920
                    },
                    navArgument("canvasH") {
                        type = NavType.IntType
                        defaultValue = 1080
                    },
                ),
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                val canvasW = backStackEntry.arguments?.getInt("canvasW") ?: 1920
                val canvasH = backStackEntry.arguments?.getInt("canvasH") ?: 1080
                EditorScreen(
                    projectId = projectId,
                    initialCanvasW = canvasW,
                    initialCanvasH = canvasH,
                    isDarkTheme = isDark,
                    onBack = { navController.popBackStackSafe() },
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStackSafe() },
                )
            }
        }
    }
}
