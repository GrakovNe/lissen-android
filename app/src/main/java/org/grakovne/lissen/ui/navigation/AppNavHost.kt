package org.grakovne.lissen.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.ImageLoader
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.LibraryScreen
import org.grakovne.lissen.ui.screens.login.LoginScreen
import org.grakovne.lissen.ui.screens.player.PlayerScreen
import org.grakovne.lissen.ui.screens.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    preferences: LissenSharedPreferences,
    networkQualityService: NetworkQualityService,
    navigationService: AppNavigationService,
    imageLoader: ImageLoader
) {
    val hasCredentials by remember {
        mutableStateOf(
            preferences.hasCredentials()
        )
    }
    val startDestination = when {
        hasCredentials -> "library_screen"
        else -> "login_screen"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("library_screen") {
            LibraryScreen(
                navController = navigationService,
                imageLoader = imageLoader,
                networkQualityService = networkQualityService
            )
        }

        composable(
            "player_screen/{bookId}?bookTitle={bookTitle}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookTitle") { type = NavType.StringType; nullable = true }
            )
        ) { navigationStack ->
            val bookId = navigationStack.arguments?.getString("bookId")
            val bookTitle = navigationStack.arguments?.getString("bookTitle")

            PlayerScreen(
                navController = navigationService,
                imageLoader = imageLoader,
                bookId = bookId,
                bookTitle = bookTitle
            )
        }

        composable("login_screen") {
            LoginScreen(navigationService)
        }

        composable("settings_screen") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                navController = navigationService
            )
        }
    }
}
