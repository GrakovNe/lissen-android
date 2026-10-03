package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import org.grakovne.lissen.playback.autoskip.durationMs
import org.grakovne.lissen.playback.autoskip.skippable
import org.grakovne.lissen.playback.service.PlaybackTimer
import org.grakovne.lissen.playback.service.SyncStateStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewinds a few seconds when playback is paused, so it continues with what was said last. Any
 * drop of playWhenReady is a pause: the user, a sleep timer, unplugged headphones, a lost audio
 * focus, the switch to another item. A seek or a stall stops the audio too and does not rewind.
 * The rewind stays inside the chapter and does not cross what the auto-skip skips. An "end of
 * episode" timer ends the episode, so its pause does not rewind either.
 */
@Singleton
@OptIn(UnstableApi::class)
class RewindOnPauseService
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val preferences: PlaybackPreferences,
    private val autoSkipPreferences: AutoSkipPreferences,
    private val syncState: SyncStateStore,
    private val playbackTimer: PlaybackTimer,
  ) : RunningComponent {
    // audio has played since the last jump or pause: without it a pause has nothing to repeat, and
    // onPlayWhenReadyChanged fires again when only the reason of the pause changes
    private var played = false

    private val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) played = true
        }

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          // the audio runs on through a chapter that follows on its own and through a gap in the source
          if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION && reason != Player.DISCONTINUITY_REASON_INTERNAL) played = false
        }

        override fun onPlayWhenReadyChanged(
          playWhenReady: Boolean,
          reason: Int,
        ) {
          if (!playWhenReady) rewind()
        }
      }

    override fun onCreate() {
      player.addListener(listener)
    }

    private fun rewind() {
      if (!played) return
      played = false

      val settings = preferences.getRewindOnPause()
      if (!settings.enabled) return
      if (playbackTimer.isEpisodeTimerExpiring) return
      if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) return

      val position = player.currentPosition
      val target = (position - settings.seconds * MILLIS_PER_SECOND).coerceAtLeast(limitMs(position))
      // a seek to the same place is still a seek for every other listener
      if (target >= position) return

      Timber.d("Rewind on pause: chapter=${player.currentMediaItemIndex}, fromMs=$position, toMs=$target")
      player.seekTo(target)
    }

    private fun limitMs(positionMs: Long): Long {
      // matched by its chapter count, as the auto-skip does
      val book = syncState.value.item?.takeIf { it.chapters.size == player.mediaItemCount } ?: return 0L
      val chapter = book.chapters.getOrNull(player.currentMediaItemIndex) ?: return 0L

      return autoSkipPreferences.get(book.id).skippable(chapter.durationMs)?.rewindLimitMs(positionMs) ?: 0L
    }

    companion object {
      private const val MILLIS_PER_SECOND = 1000L
    }
  }
