package org.grakovne.lissen.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

class AppNavigationService(
  private val host: NavHostController,
) {
  fun showLibrary(clearHistory: Boolean = false) {
    host.navigate(ROUTE_LIBRARY) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) {
        inclusive = clearHistory
        saveState = !clearHistory
      }

      launchSingleTop = true
      restoreState = !clearHistory
    }
  }

  fun showLinkedSearch(token: String) {
    host.navigate(libraryRoute(token)) { launchSingleTop = true }
  }

  fun goBack() {
    if (host.previousBackStackEntry != null) {
      host.popBackStack()
    }
  }

  fun showPlayer(
    bookId: String,
    bookTitle: String,
    bookSubtitle: String?,
    startInstantly: Boolean = false,
  ) {
    host.navigate(playerRoute(bookId, bookTitle, bookSubtitle, startInstantly)) {
      launchSingleTop = true
    }
  }

  fun showSettings() = host.navigate(ROUTE_SETTINGS)

  fun showCustomHeadersSettings() = host.navigate(ROUTE_SETTINGS_CUSTOM_HEADERS)

  fun showConnectionSettings() = host.navigate(ROUTE_SETTINGS_CONNECTION)

  fun showLocalUrlSettings() = host.navigate(ROUTE_SETTINGS_LOCAL_URL)

  fun showClientCertificateSettings() = host.navigate(ROUTE_SETTINGS_CLIENT_CERTIFICATE)

  fun showSeekSettings() = host.navigate(ROUTE_SETTINGS_SEEK)

  fun showCachedItemsSettings() = host.navigate(ROUTE_SETTINGS_CACHED_ITEMS)

  fun showCacheSettings() = host.navigate(ROUTE_SETTINGS_CACHE)

  fun showAdvancedSettings() = host.navigate(ROUTE_SETTINGS_ADVANCED)

  fun showPlaybackPreferences() = host.navigate(ROUTE_SETTINGS_PLAYBACK)

  fun showAppearancePreferences() = host.navigate(ROUTE_SETTINGS_APPEARANCE)

  fun showLogin() {
    host.navigate(ROUTE_LOGIN) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) { inclusive = true }

      launchSingleTop = true
    }
  }
}
