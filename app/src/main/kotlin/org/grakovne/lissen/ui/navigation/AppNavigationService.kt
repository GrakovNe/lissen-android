package org.grakovne.lissen.ui.navigation

import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

class AppNavigationService(
  private val host: NavHostController,
) {
  fun showLibrary(clearHistory: Boolean = false) {
    host.navigate(ROUTE_LIBRARY) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) { inclusive = clearHistory }

      launchSingleTop = true
    }
  }

  fun showPlayer(
    bookId: String,
    bookTitle: String,
    bookSubtitle: String?,
    startInstantly: Boolean = false,
  ) {
    val route =
      buildString {
        append("$ROUTE_PLAYER/$bookId")
        append("?bookTitle=${Uri.encode(bookTitle)}")
        append("&bookSubtitle=${Uri.encode(bookSubtitle ?: "")}")
        append("&startInstantly=$startInstantly")
      }
    host.navigate(route) { launchSingleTop = true }
  }

  fun showSettings() = host.navigate(ROUTE_SETTINGS)

  fun showCustomHeadersSettings() = host.navigate("$ROUTE_SETTINGS/custom_headers")

  fun showSeekSettings() = host.navigate("$ROUTE_SETTINGS/seek_settings")

  fun showCachedItemsSettings() = host.navigate("$ROUTE_SETTINGS/cached_items")

  fun showCacheSettings() = host.navigate("$ROUTE_SETTINGS/cache_settings")

  fun showAdvancedSettings() = host.navigate("$ROUTE_SETTINGS/advanced_settings")

  fun showLogin() {
    host.navigate(ROUTE_LOGIN) {
      val startId = host.graph.findStartDestination().id
      popUpTo(startId) { inclusive = true }

      launchSingleTop = true
    }
  }
}
