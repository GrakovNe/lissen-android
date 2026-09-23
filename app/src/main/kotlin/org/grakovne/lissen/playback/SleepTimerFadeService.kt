package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fades the volume to silence before the sleep timer pauses playback. One ramp: the first tick
 * inside the window captures the volume and walks it linearly to zero at expiry; later ticks
 * never retime it. The volume is never raised while playing: after an expiry it stays at zero
 * until the player stops, while a cancellation restores it at once.
 */
@Singleton
class SleepTimerFadeService
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val playbackEventBus: PlaybackEventBus,
    private val preferences: PlaybackPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var fadeJob: Job? = null
    private var fading = false
    private var awaitingRestore = false
    private var originalVolume = 1f

    private val playerListener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (!isPlaying) {
            restoreAfterPlaybackStopped()
          }
        }
      }

    override fun onCreate() {
      player.addListener(playerListener)

      scope.launch {
        playbackEventBus.events.collect { event ->
          when (event) {
            is PlaybackEvent.TimerTick -> {
              onTick(event.remainingSeconds)
            }

            PlaybackEvent.TimerExpired -> {
              onTimerExpired()
            }

            PlaybackEvent.TimerCancelled -> {
              onTimerCancelled()
            }

            else -> {}
          }
        }
      }
    }

    private fun onTick(remainingSeconds: Long) {
      if (fading) return

      val settings = preferences.getSleepTimerSettings()
      if (!settings.fadeEnabled) return

      if (remainingSeconds <= 0L || remainingSeconds > settings.fadeSeconds) return

      startFade(remainingSeconds)
    }

    private fun startFade(remainingSeconds: Long) {
      fading = true
      originalVolume = player.volume

      val durationMillis = remainingSeconds * MILLIS_PER_SECOND
      Timber.d("Sleep timer fade started: volume=$originalVolume, durationMillis=$durationMillis")

      fadeJob =
        scope.launch {
          var elapsedMillis = 0L

          while (elapsedMillis < durationMillis) {
            delay(FADE_STEP_MILLIS)
            elapsedMillis += FADE_STEP_MILLIS
            player.volume = fadeVolumeAt(originalVolume, elapsedMillis, durationMillis)
          }

          player.volume = 0f
        }
    }

    private fun onTimerExpired() {
      fadeJob?.cancel()
      fadeJob = null

      if (!fading) return

      // Pin silence at the exact moment of the pause, even if the ramp is slightly behind.
      player.volume = 0f
      fading = false

      if (player.isPlaying) {
        awaitingRestore = true
      } else {
        restoreVolume()
      }
    }

    private fun onTimerCancelled() {
      fadeJob?.cancel()
      fadeJob = null

      // after an expiry `fading` is already cleared, so a trailing cancellation keeps the silence
      if (fading) {
        fading = false
        restoreVolume()
      }
    }

    private fun restoreAfterPlaybackStopped() {
      if (!awaitingRestore) return

      restoreVolume()
    }

    private fun restoreVolume() {
      awaitingRestore = false
      player.volume = originalVolume
      Timber.d("Sleep timer fade reverted: volume=$originalVolume")
    }

    companion object {
      private const val MILLIS_PER_SECOND = 1000L
      private const val FADE_STEP_MILLIS = 50L
    }
  }

internal fun fadeVolumeAt(
  originalVolume: Float,
  elapsedMillis: Long,
  durationMillis: Long,
): Float =
  if (durationMillis <= 0L) {
    0f
  } else {
    val fraction = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
    (originalVolume * (1f - fraction)).coerceIn(0f, 1f)
  }
