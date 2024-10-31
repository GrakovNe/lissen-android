package org.grakovne.lissen.ui.navigation

import androidx.navigation.NavHostController

class AppNavigationService(
    private val host: NavHostController
) {

    fun showLibrary() {
        host.navigate("library_screen") {
            launchSingleTop = true

            popUpTo(host.graph.startDestinationId) {
                inclusive = true
            }
        }
    }

    fun showPlayer(bookId: String, bookName: String) {
        host.navigate("player_screen/$bookId?bookTitle=$bookName") {
            launchSingleTop = true

            host.currentBackStackEntry?.arguments?.putString("bookTitle", bookName)
        }
    }

    fun showSettings() {
        host.navigate("settings_screen")
    }

    fun showLogin() {
        host.navigate("login_screen") {
            popUpTo(0) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }
}
