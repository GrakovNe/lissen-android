package org.grakovne.lissen.ui.navigation

import android.annotation.SuppressLint
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import org.grakovne.lissen.ui.screens.settings.advanced.CustomHeadersSettingsScreen

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppNavHost(
    navController: NavHostController,
    preferences: LissenSharedPreferences,
    networkQualityService: NetworkQualityService,
    navigationService: AppNavigationService,
    imageLoader: ImageLoader,
) {
    val hasCredentials by remember {
        mutableStateOf(
            preferences.hasCredentials(),
        )
    }
    val startDestination = when {
        hasCredentials -> "library_screen"
        else -> "login_screen"
    }

    val enterTransition: EnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

    val exitTransition: ExitTransition = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

    val popEnterTransition: EnterTransition = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

    val popExitTransition: ExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable("library_screen") {
                LibraryScreen(
                    navController = navigationService,
                    imageLoader = imageLoader,
                    networkQualityService = networkQualityService,
                )
            }

            composable(
                route = "player_screen/{bookId}?bookTitle={bookTitle}",
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType },
                    navArgument("bookTitle") { type = NavType.StringType; nullable = true },
                ),
                enterTransition = { enterTransition },
                exitTransition = { exitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition },
            ) { navigationStack ->
                val bookId = navigationStack.arguments?.getString("bookId") ?: return@composable
                val bookTitle = navigationStack.arguments?.getString("bookTitle") ?: ""

                PlayerScreen(
                    navController = navigationService,
                    imageLoader = imageLoader,
                    bookId = bookId,
                    bookTitle = bookTitle,
                )
            }

            composable(
                route = "login_screen",
                enterTransition = { enterTransition },
                exitTransition = { exitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition },
            ) {
                LoginScreen(navigationService)
            }

            composable(
                route = "settings_screen",
                enterTransition = { enterTransition },
                exitTransition = { exitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition },
            ) {
                SettingsScreen(
                    onBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    },
                    navController = navigationService,
                )
            }

            composable(
                route = "settings_screen/custom_headers",
                enterTransition = { enterTransition },
                exitTransition = { exitTransition },
                popEnterTransition = { popEnterTransition },
                popExitTransition = { popExitTransition },
            ) {
                CustomHeadersSettingsScreen(
                    onBack = {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}
