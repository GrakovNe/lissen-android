package org.grakovne.lissen.widget

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val sharedPreferences: PlaybackPreferences,
  ) {
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
      val libraryType =
        mediaRepository.playingBook.value
          ?.takeIf { it.id == itemId }
          ?.libraryType
          ?: sharedPreferences.getPlayingItem()?.takeIf { it.id == itemId }?.libraryType

      mediaRepository.clearPreparedItem()
      mediaRepository.preparePlayback(bookId = itemId, libraryType = libraryType)

      val prepared =
        combine(
          mediaRepository.playingBook,
          mediaRepository.isPlaybackReady,
          mediaRepository.mediaPreparingError,
        ) { book, ready, failed ->
          when {
            failed -> false
            book?.id == itemId && ready -> true
            else -> null
          }
        }.filterNotNull()
          .first()

      if (prepared) onPlaybackReady()
    }
  }
