package org.grakovne.lissen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import org.grakovne.lissen.ui.screens.library.LibraryScreen
import org.grakovne.lissen.ui.screens.login.LoginScreen
import org.grakovne.lissen.ui.screens.player.composable.PlayerScreen
import org.grakovne.lissen.ui.screens.settings.SettingsScreen
import org.grakovne.lissen.viewmodel.ConnectionViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun AppNavHost(navController: NavHostController) {
    val serverPrefs = remember { ServerConnectionPreferences.getInstance() }
    val hasCredentials by remember { mutableStateOf(serverPrefs.hasCredentials()) }

    val startDestination = if (hasCredentials) "settings_screen" else "login_screen"


    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable("library_screen") {
            LibraryScreen(navController)
        }

        composable("player_screen") {
            PlayerScreen(
                viewModel = PlayerViewModel(),
                navController = navController,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("login_screen") {
            LoginScreen(navController)
        }

        composable("settings_screen") {
            SettingsScreen(
                viewModel = ConnectionViewModel(),
                onBack = { navController.popBackStack() })
        }
    }
}
