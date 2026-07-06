package org.grakovne.lissen.widget

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class WidgetPlaybackController
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val sharedPreferences: PlaybackPreferences,
    private val playbackEventBus: PlaybackEventBus,
  ) {
    private var playbackReadyAction: () -> Unit = {}

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
      scope.launch {
        playbackEventBus.events.collect { event ->
          if (event is PlaybackEvent.PlaybackReady) {
            val book = sharedPreferences.getPlayingItem()
            book?.let {
              playbackReadyAction
                .invoke()
                .also { playbackReadyAction = { } }
            }
          }
        }
      }
    }

    fun providePlayingItem() = mediaRepository.playingBook.value

    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack(false)

    fun rewind() = mediaRepository.rewind()

    fun forward() = mediaRepository.forward()

    suspend fun prepareAndRun(
      itemId: String,
      onPlaybackReady: () -> Unit,
    ) {
      playbackReadyAction = onPlaybackReady
      mediaRepository.preparePlayback(bookId = itemId)
    }
  }
