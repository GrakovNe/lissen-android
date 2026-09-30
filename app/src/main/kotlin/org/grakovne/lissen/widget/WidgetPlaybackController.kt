package org.grakovne.lissen.widget

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class WidgetPlaybackController
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val preferences: PlaybackPreferences,
  ) {
    fun togglePlayPause() = mediaRepository.togglePlayPause()

    fun nextTrack() = mediaRepository.nextTrack()

    fun previousTrack() = mediaRepository.previousTrack(false)

    fun rewind() = mediaRepository.rewind()

    fun forward() = mediaRepository.forward()

    suspend fun runForItem(
      itemId: String,
      onPlaybackReady: () -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
      if (mediaRepository.playingBook.value?.id != itemId) {
        val libraryType = preferences.getLastPlayingItem()?.takeIf { it.id == itemId }?.libraryType

        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId = itemId, libraryType = libraryType)
      }

      val prepared =
        combine(
          mediaRepository.playingBook,
          mediaRepository.isPlaybackReady,
          mediaRepository.mediaPreparingError,
        ) { book, ready, failed ->
          // a loaded book stays usable after a playback error; the error only fails a preparation
          when {
            book?.id == itemId && ready -> true
            failed -> false
            else -> null
          }
        }.filterNotNull()
          .first()

      if (prepared) onPlaybackReady()
    }
  }
