package org.grakovne.lissen.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.PendingConfigImport
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

  @Inject
  lateinit var pendingConfigImport: PendingConfigImport

  private lateinit var appNavigationService: AppNavigationService

  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    consumeConfigImportIntent(intent)

    setContent {
      val colorScheme by preferences
        .colorSchemeFlow
        .collectAsState(initial = preferences.getColorScheme())

      val materialYou by preferences
        .materialYouFlow
        .collectAsState(initial = preferences.getMaterialYouColors())

      LissenTheme(colorScheme, materialYou) {
        val navController = rememberNavController()
        appNavigationService =
          remember(navController) { AppNavigationService(navController) }

        Box(
          modifier =
            Modifier
              .fillMaxSize()
              .semantics { testTagsAsResourceId = true },
        ) {
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
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // launchMode is singleTop, so a warm start delivers the new intent here instead of
    // re-running onCreate. Act on it imperatively rather than relying on the start destination.
    setIntent(intent)

    if (!::appNavigationService.isInitialized) {
      return
    }

    when (getLaunchAction(intent)) {
      AppLaunchAction.CONTINUE_PLAYBACK -> {
        preferences.getPlayingItem()?.let { book ->
          appNavigationService.showPlayer(
            bookId = book.id,
            bookTitle = book.title,
            bookSubtitle = book.subtitle,
            startInstantly = true,
          )
        }
      }

      AppLaunchAction.MANAGE_DOWNLOADS -> {
        appNavigationService.showCachedItemsSettings()
      }

      AppLaunchAction.IMPORT_CONFIG -> {
        if (consumeConfigImportIntent(intent)) {
          appNavigationService.showConfigBackupSettings()
        }
      }

      AppLaunchAction.DEFAULT -> {
        Unit
      }
    }
  }

  /**
   * Reads the file behind an incoming settings-backup VIEW intent (e.g. tapping the exported
   * .lsettings file in a file manager) and stashes it for SettingsViewModel to pick up once the
   * Backup & Restore screen is on screen, instead of making the user re-pick the same file via SAF.
   */
  private fun consumeConfigImportIntent(intent: Intent?): Boolean {
    if (!isConfigImportIntent(intent)) return false
    val uri = intent?.data ?: return false

    val json =
      runCatching {
        contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
      }.getOrNull() ?: return false

    pendingConfigImport.offer(json)
    return true
  }

  // Matched by filename extension rather than MIME type, so the app doesn't show up as an
  // "Open with" candidate for arbitrary JSON files - only for its own exported backups.
  private fun isConfigImportIntent(intent: Intent?): Boolean =
    intent?.action == Intent.ACTION_VIEW &&
      intent.data?.lastPathSegment?.endsWith(CONFIG_FILE_EXTENSION) == true

  private fun getLaunchAction(intent: Intent?): AppLaunchAction {
    val action =
      when {
        isConfigImportIntent(intent) -> AppLaunchAction.IMPORT_CONFIG
        intent?.action == CONTINUE_PLAYBACK -> AppLaunchAction.CONTINUE_PLAYBACK
        intent?.action == SHOW_DOWNLOADS -> AppLaunchAction.MANAGE_DOWNLOADS
        else -> AppLaunchAction.DEFAULT
      }
    Timber.d("App launched: action=$action (intent=${intent?.action})")
    return action
  }

  private companion object {
    private const val CONFIG_FILE_EXTENSION = ".lsettings"
  }
}
