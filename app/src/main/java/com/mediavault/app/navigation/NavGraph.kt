package com.mediavault.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mediavault.app.ui.browser.BrowserScreen
import com.mediavault.app.ui.home.HomeScreen
import com.mediavault.app.ui.library.LibraryScreen
import com.mediavault.app.ui.onboarding.OnboardingScreen
import com.mediavault.app.ui.player.AudioPlayerScreen
import com.mediavault.app.ui.player.VideoPlayerScreen
import com.mediavault.app.ui.queue.QueueScreen
import com.mediavault.app.ui.settings.SettingsScreen
import com.mediavault.app.ui.settings.SettingsViewModel
import com.mediavault.app.ui.upscale.UpscaleScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Browser : Screen("browser")
    object Queue : Screen("queue")
    object Library : Screen("library")
    object Upscale : Screen("upscale")
    object Settings : Screen("settings")
    object VideoPlayer : Screen("video_player/{filePath}") {
        fun createRoute(path: String) = "video_player/$path"
    }
    object AudioPlayer : Screen("audio_player/{filePath}") {
        fun createRoute(path: String) = "audio_player/$path"
    }
}

@Composable
fun MainAppNavigation(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val settings by settingsViewModel.settings.collectAsState()

    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Browser.route,
        Screen.Queue.route,
        Screen.Library.route,
        Screen.Upscale.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    isBrowserModeEnabled = settings.isBrowserModeEnabled,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(onFinish = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    navController = navController,
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Browser.route) {
                BrowserScreen(navController = navController)
            }
            composable(Screen.Queue.route) {
                QueueScreen(navController = navController)
            }
            composable(Screen.Library.route) {
                LibraryScreen(navController = navController)
            }
            composable(Screen.Upscale.route) {
                UpscaleScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(navController = navController)
            }
            composable(
                route = Screen.VideoPlayer.route,
                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
            ) { backStack ->
                val encodedPath = backStack.arguments?.getString("filePath") ?: ""
                val decodedPath = android.net.Uri.decode(encodedPath)
                VideoPlayerScreen(
                    filePath = decodedPath,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.AudioPlayer.route,
                arguments = listOf(navArgument("filePath") { type = NavType.StringType })
            ) { backStack ->
                val encodedPath = backStack.arguments?.getString("filePath") ?: ""
                val decodedPath = android.net.Uri.decode(encodedPath)
                AudioPlayerScreen(
                    filePath = decodedPath,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
