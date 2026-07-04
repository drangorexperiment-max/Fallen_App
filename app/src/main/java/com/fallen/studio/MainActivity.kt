package com.fallen.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
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

        NavHost(
            navController = navController,
            startDestination = "splash",
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
                        navController.navigate(route)
                    },
                    onOpenSettings = { navController.navigate("settings") },
                )
            }

            composable(
                route = "editor?projectId={projectId}",
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId")
                EditorScreen(
                    projectId = projectId,
                    isDarkTheme = isDark,
                    onBack = { navController.popBackStack() },
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
