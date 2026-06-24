package org.grakovne.lissen.ui.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.navigation.AppLaunchAction
import org.grakovne.lissen.ui.navigation.AppNavHost
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.navigation.CONTINUE_PLAYBACK
import org.grakovne.lissen.ui.navigation.SHOW_DOWNLOADS
import org.grakovne.lissen.ui.theme.LissenTheme
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AppActivity : ComponentActivity() {
  @Inject
  lateinit var preferences: LissenSharedPreferences

  @Inject
  lateinit var imageLoader: ImageLoader

  @Inject
  lateinit var networkService: NetworkService

  private lateinit var appNavigationService: AppNavigationService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Both bars must be fully transparent so the app background shows through identically. The
    // default enableEdgeToEdge() leaves a contrast scrim on the navigation bar only, which makes
    // the bottom bar a different shade from the (transparent) status bar.
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    )

    setContent {
      val colorScheme by preferences
        .colorSchemeFlow
        .collectAsState(initial = preferences.getColorScheme())

      val materialYou by preferences
        .materialYouFlow
        .collectAsState(initial = preferences.getMaterialYouColors())

      LissenTheme(colorScheme, materialYou) {
        val navController = rememberNavController()
        appNavigationService = AppNavigationService(navController)

        AppNavHost(
          navController = navController,
          navigationService = appNavigationService,
          preferences = preferences,
          imageLoader = imageLoader,
          networkService = networkService,
          appLaunchAction = getLaunchAction(intent),
        )
      }
    }
  }

  private fun getLaunchAction(intent: Intent?): AppLaunchAction {
    val action =
      when (intent?.action) {
        CONTINUE_PLAYBACK -> AppLaunchAction.CONTINUE_PLAYBACK
        SHOW_DOWNLOADS -> AppLaunchAction.MANAGE_DOWNLOADS
        else -> AppLaunchAction.DEFAULT
      }
    Timber.d("App launched: action=$action (intent=${intent?.action})")
    return action
  }
}
