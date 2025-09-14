package org.grakovne.lissen.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.navigation.AppLaunchAction
import org.grakovne.lissen.ui.navigation.AppNavHost
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.navigation.CONTINUE_PLAYBACK
import org.grakovne.lissen.ui.navigation.SHOW_DOWNLOADS
import org.grakovne.lissen.ui.theme.LissenTheme
import javax.inject.Inject

@AndroidEntryPoint
class AppActivity : ComponentActivity() {
  @Inject
  lateinit var preferences: LissenSharedPreferences

  @Inject
  lateinit var imageLoader: ImageLoader

  @Inject
  lateinit var networkQualityService: NetworkQualityService

  private lateinit var appNavigationService: AppNavigationService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      val colorScheme by preferences
        .colorSchemeFlow
        .collectAsState(initial = preferences.getColorScheme())

      LissenTheme(colorScheme) {
        val navController = rememberNavController()
        appNavigationService = AppNavigationService(navController)

        AppNavHost(
          navController = navController,
          navigationService = appNavigationService,
          preferences = preferences,
          imageLoader = imageLoader,
          networkQualityService = networkQualityService,
          appLaunchAction = getLaunchAction(intent),
        )
      }
    }
  }

  private fun getLaunchAction(intent: Intent?) =
    when (intent?.action) {
      CONTINUE_PLAYBACK -> AppLaunchAction.CONTINUE_PLAYBACK
      SHOW_DOWNLOADS -> AppLaunchAction.MANAGE_DOWNLOADS
      else -> AppLaunchAction.DEFAULT
    }
}
