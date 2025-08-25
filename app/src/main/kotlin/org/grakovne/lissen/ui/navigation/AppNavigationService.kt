package org.grakovne.lissen.ui.navigation

import android.net.Uri
import androidx.navigation.NavHostController

class AppNavigationService(
  private val host: NavHostController,
) {
  fun showLibrary(clearHistory: Boolean = false) {
    host.navigate(ROUTE_LIBRARY) {
      launchSingleTop = true
      popUpTo(host.graph.startDestinationId) { inclusive = clearHistory }
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

  fun showLogin() {
    host.navigate(ROUTE_LOGIN) {
      popUpTo(0) { inclusive = true }
      launchSingleTop = true
    }
  }

  private companion object {
    const val ROUTE_LIBRARY = "library_screen"
    const val ROUTE_PLAYER = "player_screen"
    const val ROUTE_SETTINGS = "settings_screen"
    const val ROUTE_LOGIN = "login_screen"
  }
}
