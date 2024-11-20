package org.grakovne.lissen.ui.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService.Companion.BOOK_EXTRA
import org.grakovne.lissen.playback.service.PlaybackService.Companion.PLAYBACK_READY
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetPlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) {

    private var playbackReadyAction: () -> Unit = {}

    private val bookDetailsReadyReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PLAYBACK_READY) {
                val book = intent.getSerializableExtra(BOOK_EXTRA) as? DetailedItem

                book?.let {
                    CoroutineScope(Dispatchers.Main).launch {
                        println("LISSENAPP: READY")

                        playbackReadyAction
                            .invoke()
                            .also { playbackReadyAction = { } }
                    }
                }
            }
        }
    }

    init {
        LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(bookDetailsReadyReceiver, IntentFilter(PLAYBACK_READY))
    }


    fun fetchPlayingBook() = mediaRepository.playingBook.value

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack()

    fun rewind() = mediaRepository.rewind()

    fun forward() = mediaRepository.forward()

    suspend fun prepareAndRun(
        itemId: String,
        onPlaybackReady: () -> Unit
    ) {
        playbackReadyAction = onPlaybackReady
        mediaRepository.preparePlayback(itemId)
    }
}