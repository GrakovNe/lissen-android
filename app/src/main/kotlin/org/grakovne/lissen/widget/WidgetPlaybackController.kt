package org.grakovne.lissen.widget

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

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

    /**
     * With nothing loaded (e.g. the process was just started by the widget tap), playback is resumed
     * through the media session, like a headset play button. Otherwise the loaded book is toggled.
     */
    suspend fun togglePlayback(itemId: String) =
      withContext(Dispatchers.Main.immediate) {
        if (resumingThroughSession) {
          Timber.d("Ignoring widget play/pause: playback resumption is already in progress")
          return@withContext
        }

        when (mediaRepository.playingBook.value) {
          null -> resumeThroughSession()
          else -> runForItem(itemId) { togglePlayPause() }
        }
      }

    suspend fun runForItem(
      itemId: String,
      onPlaybackReady: () -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
      val targetId =
        itemId.ifBlank { mediaRepository.playingBook.value?.id ?: preferences.getLastPlayingItem()?.id }
          ?: run {
            Timber.d("Ignoring widget action: no book to act on")
            return@withContext
          }

      if (mediaRepository.playingBook.value?.id != targetId) {
        val libraryType = preferences.getLastPlayingItem()?.takeIf { it.id == targetId }?.libraryType

        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId = targetId, libraryType = libraryType)
      }

      val prepared =
        combine(
          mediaRepository.playingBook,
          mediaRepository.isPlaybackReady,
          mediaRepository.mediaPreparingError,
        ) { book, ready, failed ->
          // a loaded book stays usable after a playback error; the error only fails the preparation
          when {
            book?.id == targetId && ready -> true
            failed -> false
            else -> null
          }
        }.filterNotNull()
          .first()

      if (prepared) onPlaybackReady()
    }

    /** Main-thread confined: every access happens inside [togglePlayback]'s main-thread context. */
    private var resumingThroughSession = false

    /**
     * Keeps the widget action (and so the process) alive until the session has finished resuming:
     * it produced a queue, it failed, or the player was already playing. Play taps during that
     * window are ignored, so a quick double tap can't resume twice. Once resumption has finished,
     * the book is registered (or nothing is), and the next tap toggles or retries as usual.
     */
    private suspend fun resumeThroughSession() {
      resumingThroughSession = true

      try {
        val succeeded =
          withTimeoutOrNull(RESUMPTION_TIMEOUT) {
            merge(
              // request only once subscribed, so an outcome can't be emitted before it is awaited
              mediaRepository.playbackResumptions.onSubscription { mediaRepository.requestSessionPlayback() },
              mediaRepository.isPlaying.filter { it },
            ).first()
          }

        when (succeeded) {
          true -> Unit
          false -> Timber.w("Widget playback resumption failed")
          null -> Timber.w("Widget playback resumption got no answer from the session in time")
        }
      } finally {
        resumingThroughSession = false
      }
    }

    private companion object {
      /** Only a safety net for a session that never answers; a resumption reports its own outcome. */
      val RESUMPTION_TIMEOUT = 8.seconds
    }
  }
