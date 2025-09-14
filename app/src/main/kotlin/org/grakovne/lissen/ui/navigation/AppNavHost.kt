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
import org.grakovne.lissen.ui.screens.settings.advanced.AdvancedSettingsComposable
import org.grakovne.lissen.ui.screens.settings.advanced.CustomHeadersSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.SeekSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CachedItemsSettingsScreen

@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun AppNavHost(
  navController: NavHostController,
  preferences: LissenSharedPreferences,
  networkQualityService: NetworkQualityService,
  navigationService: AppNavigationService,
  imageLoader: ImageLoader,
  appLaunchAction: AppLaunchAction,
) {
  val hasCredentials by remember {
    mutableStateOf(
      preferences.hasCredentials(),
    )
  }

  val book = preferences.getPlayingBook()

  val startDestination =
    when {
      appLaunchAction == AppLaunchAction.MANAGE_DOWNLOADS -> "$ROUTE_SETTINGS/cached_items"
      hasCredentials.not() -> ROUTE_LOGIN
      appLaunchAction == AppLaunchAction.CONTINUE_PLAYBACK && book != null ->
        "$ROUTE_PLAYER/${book.id}?bookTitle=${book.title}&bookSubtitle=${book.subtitle}&startInstantly=true"
      else -> ROUTE_LIBRARY
    }

  val enterTransition: EnterTransition =
    slideInHorizontally(
      initialOffsetX = { it },
      animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

  val exitTransition: ExitTransition =
    slideOutHorizontally(
      targetOffsetX = { -it },
      animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

  val popEnterTransition: EnterTransition =
    slideInHorizontally(
      initialOffsetX = { -it },
      animationSpec = tween(),
    ) + fadeIn(animationSpec = tween())

  val popExitTransition: ExitTransition =
    slideOutHorizontally(
      targetOffsetX = { it },
      animationSpec = tween(),
    ) + fadeOut(animationSpec = tween())

  Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
    NavHost(
      navController = navController,
      startDestination = startDestination,
    ) {
      composable("settings_screen/cached_items") {
        CachedItemsSettingsScreen(
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
          imageLoader = imageLoader,
        )
      }
      composable("library_screen") {
        LibraryScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          networkQualityService = networkQualityService,
        )
      }

      composable(
        route = "player_screen/{bookId}?bookTitle={bookTitle}&bookSubtitle={bookSubtitle}&startInstantly={startInstantly}",
        arguments =
          listOf(
            navArgument("bookId") { type = NavType.StringType },
            navArgument("bookTitle") {
              type = NavType.StringType
              nullable = true
            },
            navArgument("bookSubtitle") {
              type = NavType.StringType
              nullable = true
            },
            navArgument("startInstantly") {
              type = NavType.BoolType
              nullable = false
            },
          ),
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) { navigationStack ->
        val bookId = navigationStack.arguments?.getString("bookId") ?: return@composable
        val bookTitle = navigationStack.arguments?.getString("bookTitle") ?: ""
        val bookSubtitle = navigationStack.arguments?.getString("bookSubtitle")
        val startInstantly = navigationStack.arguments?.getBoolean("startInstantly")

        PlayerScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          bookId = bookId,
          bookTitle = bookTitle,
          bookSubtitle = bookSubtitle,
          playInstantly = startInstantly ?: false,
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

      composable(
        route = "settings_screen/advanced_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        AdvancedSettingsComposable(
          navController = navigationService,
          onBack = {
            if (navController.previousBackStackEntry != null) {
              navController.popBackStack()
            }
          },
        )
      }

      composable(
        route = "settings_screen/seek_settings",
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition },
      ) {
        SeekSettingsScreen(
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
