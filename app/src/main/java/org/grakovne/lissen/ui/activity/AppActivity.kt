package org.grakovne.lissen.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackService.Companion.BOOK_EXTRA
import org.grakovne.lissen.ui.navigation.AppNavHost
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.theme.LissenTheme
import javax.inject.Inject

@AndroidEntryPoint
class AppActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: LissenSharedPreferences

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var playbackLauncher: PlaybackLauncher

    @Inject
    lateinit var networkQualityService: NetworkQualityService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (intent?.action == "org.grakovne.lissen.action.PLAY_LAST") {
            handlePlayLastShortcut()
        }

        setContent {
            val colorScheme by preferences
                .colorSchemeFlow
                .collectAsState(initial = preferences.getColorScheme())

            LissenTheme(colorScheme) {
                val navController = rememberNavController()

                AppNavHost(
                    navController = navController,
                    navigationService = AppNavigationService(navController),
                    preferences = preferences,
                    imageLoader = imageLoader,
                    networkQualityService = networkQualityService,
                )
            }
        }
    }

    private fun handlePlayLastShortcut() {
        val lastBookId = preferences.getPlayingBook() ?: return

        lifecycleScope.launch {
            playbackLauncher.prepareAndPlay(lastBookId)
            finish()
        }
    }

}
