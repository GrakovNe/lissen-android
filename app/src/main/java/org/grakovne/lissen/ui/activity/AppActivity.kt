package org.grakovne.lissen.ui.activity

import android.os.Bundle
import android.util.Log
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
import org.grakovne.lissen.ui.navigation.AppNavHost
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.theme.LissenTheme
import org.grakovne.lissen.widget.MediaRepository
import javax.inject.Inject

@AndroidEntryPoint
class AppActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: LissenSharedPreferences

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var networkQualityService: NetworkQualityService

    @Inject
    lateinit var mediaRepository: MediaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restorePlayingState()

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

    private fun restorePlayingState() {
        try {
            val playingBook = preferences.getPlayingBook()

            if (playingBook?.id != null) {
                lifecycleScope.launch {
                    mediaRepository.preparePlayback(playingBook.id)
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Unable to restore playing state due to: $ex")
        }
    }

    companion object {
        const val TAG = "AppActivity"
    }
}
