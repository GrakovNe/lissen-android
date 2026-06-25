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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil3.ImageLoader
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.screens.library.LibraryScreen
import org.grakovne.lissen.ui.screens.login.LoginScreen
import org.grakovne.lissen.ui.screens.player.PlayerScreen
import org.grakovne.lissen.ui.screens.settings.SettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.AdvancedSettingsComposable
import org.grakovne.lissen.ui.screens.settings.advanced.AppearancePreferencesScreen
import org.grakovne.lissen.ui.screens.settings.advanced.ClientCertificateSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.ConnectionSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.CustomHeadersSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.LocalUrlSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.PlaybackPreferencesScreen
import org.grakovne.lissen.ui.screens.settings.advanced.SeekSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CacheSettingsScreen
import org.grakovne.lissen.ui.screens.settings.advanced.cache.CachedItemsSettingsScreen

private val enterTransition: EnterTransition =
  slideInHorizontally(initialOffsetX = { it }, animationSpec = tween()) +
    fadeIn(animationSpec = tween())

private val exitTransition: ExitTransition =
  slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween()) +
    fadeOut(animationSpec = tween())

private val popEnterTransition: EnterTransition =
  slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween()) +
    fadeIn(animationSpec = tween())

private val popExitTransition: ExitTransition =
  slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween()) +
    fadeOut(animationSpec = tween())

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun AppNavHost(
  navController: NavHostController,
  preferences: LissenSharedPreferences,
  networkService: NetworkService,
  navigationService: AppNavigationService,
  imageLoader: ImageLoader,
  appLaunchAction: AppLaunchAction,
) {
  // Only consumed once, when the graph is first laid out, so resolve it a single time rather
  // than re-reading preferences on every recomposition.
  val startDestination =
    remember {
      val book = preferences.getPlayingItem()

      when {
        preferences.hasCredentials().not() -> {
          ROUTE_LOGIN
        }

        appLaunchAction == AppLaunchAction.MANAGE_DOWNLOADS -> {
          ROUTE_SETTINGS_CACHED_ITEMS
        }

        appLaunchAction == AppLaunchAction.CONTINUE_PLAYBACK && book != null -> {
          playerRoute(book.id, book.title, book.subtitle, startInstantly = true)
        }

        else -> {
          ROUTE_LIBRARY
        }
      }
    }

  Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
    NavHost(
      navController = navController,
      startDestination = startDestination,
      modifier = Modifier.fillMaxSize(),
      enterTransition = { enterTransition },
      exitTransition = { exitTransition },
      popEnterTransition = { popEnterTransition },
      popExitTransition = { popExitTransition },
    ) {
      composable(route = ROUTE_SETTINGS_CACHED_ITEMS) {
        CachedItemsSettingsScreen(
          onBack = navigationService::goBack,
          imageLoader = imageLoader,
        )
      }

      composable(route = ROUTE_SETTINGS_CACHE) {
        CacheSettingsScreen(
          navController = navigationService,
          onBack = navigationService::goBack,
        )
      }

      composable(
        route = ROUTE_LIBRARY_PATTERN,
        arguments =
          listOf(
            navArgument(ARG_LINKED_SEARCH_TOKEN) {
              type = NavType.StringType
              nullable = true
            },
          ),
      ) { backStackEntry ->
        // The Navigation component already URL-decodes argument values, so read them as-is.
        val linkedSearchToken = backStackEntry.arguments?.getString(ARG_LINKED_SEARCH_TOKEN)

        LibraryScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          networkService = networkService,
          linkedSearchToken = linkedSearchToken,
        )
      }

      composable(
        route = ROUTE_PLAYER_PATTERN,
        arguments =
          listOf(
            navArgument(ARG_BOOK_ID) { type = NavType.StringType },
            navArgument(ARG_BOOK_TITLE) {
              type = NavType.StringType
              nullable = true
            },
            navArgument(ARG_BOOK_SUBTITLE) {
              type = NavType.StringType
              nullable = true
            },
            navArgument(ARG_START_INSTANTLY) {
              type = NavType.BoolType
              nullable = false
            },
          ),
      ) { navigationStack ->
        val bookId = navigationStack.arguments?.getString(ARG_BOOK_ID) ?: return@composable
        val bookTitle = navigationStack.arguments?.getString(ARG_BOOK_TITLE) ?: ""
        val bookSubtitle = navigationStack.arguments?.getString(ARG_BOOK_SUBTITLE)
        val startInstantly = navigationStack.arguments?.getBoolean(ARG_START_INSTANTLY) ?: false

        PlayerScreen(
          navController = navigationService,
          imageLoader = imageLoader,
          bookId = bookId,
          bookTitle = bookTitle,
          bookSubtitle = bookSubtitle,
          playInstantly = startInstantly,
        )
      }

      composable(route = ROUTE_LOGIN) {
        LoginScreen(navigationService)
      }

      composable(route = ROUTE_SETTINGS) {
        SettingsScreen(
          onBack = navigationService::goBack,
          navController = navigationService,
        )
      }

      composable(route = ROUTE_SETTINGS_LOCAL_URL) {
        LocalUrlSettingsScreen(onBack = navigationService::goBack)
      }

      composable(route = ROUTE_SETTINGS_CUSTOM_HEADERS) {
        CustomHeadersSettingsScreen(onBack = navigationService::goBack)
      }

      composable(route = ROUTE_SETTINGS_CLIENT_CERTIFICATE) {
        ClientCertificateSettingsScreen(onBack = navigationService::goBack)
      }

      composable(route = ROUTE_SETTINGS_CONNECTION) {
        ConnectionSettingsScreen(
          navController = navigationService,
          onBack = navigationService::goBack,
        )
      }

      composable(route = ROUTE_SETTINGS_ADVANCED) {
        AdvancedSettingsComposable(onBack = navigationService::goBack)
      }

      composable(route = ROUTE_SETTINGS_SEEK) {
        SeekSettingsScreen(onBack = navigationService::goBack)
      }

      composable(route = ROUTE_SETTINGS_PLAYBACK) {
        PlaybackPreferencesScreen(
          navController = navigationService,
          onBack = navigationService::goBack,
        )
      }

      composable(route = ROUTE_SETTINGS_APPEARANCE) {
        AppearancePreferencesScreen(onBack = navigationService::goBack)
      }
    }
  }
}
