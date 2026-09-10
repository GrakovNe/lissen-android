package org.grakovne.lissen.playback

import androidx.annotation.OptIn
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
 * Smoothly fades playback volume down to silence while the sleep timer is within
 * the configured fade window, and reverts the volume back when the timer stops.
 *
 * Timer ticks arrive with one-second resolution. Each tick inside the window starts
 * a short ramp to the new target, which turns per-second steps into a continuous descent.
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
    private var fadeStarted = false
    private var originalVolume = 1f

    override fun onCreate() {
      scope.launch {
        playbackEventBus.events.collect { event ->
          when (event) {
            is PlaybackEvent.TimerTick -> {
              onTick(event.remainingSeconds)
            }

            PlaybackEvent.TimerExpired -> {
              restoreVolume()
            }

            PlaybackEvent.TimerCancelled -> {
              restoreVolume()
            }

            else -> {}
          }
        }
      }
    }

    private fun onTick(remainingSeconds: Long) {
      val fadeSeconds =
        if (preferences.isSleepTimerFadeEnabled()) {
          preferences.getSleepTimerFadeSeconds()
        } else {
          0
        }

      if (fadeSeconds <= 0 || remainingSeconds > fadeSeconds) {
        restoreVolume()
        return
      }

      if (!fadeStarted) {
        fadeStarted = true
        originalVolume = player.volume
        Timber.d("Sleep timer fade started: volume=$originalVolume, fadeSeconds=$fadeSeconds")
      }

      val target = computeFadeVolume(remainingSeconds, fadeSeconds, originalVolume)
      fadeJob?.cancel()
      fadeJob = scope.launch { rampTo(target) }
    }

    private suspend fun rampTo(target: Float) {
      val start = player.volume
      repeat(RAMP_STEPS) { step ->
        val fraction = (step + 1f) / RAMP_STEPS
        player.volume = (start + (target - start) * fraction).coerceIn(0f, 1f)
        delay(RAMP_INTERVAL_MILLIS)
      }
    }

    private fun restoreVolume() {
      fadeJob?.cancel()
      fadeJob = null

      if (fadeStarted) {
        player.volume = originalVolume
        fadeStarted = false
        Timber.d("Sleep timer fade reverted: volume=$originalVolume")
      }
    }

    companion object {
      private const val RAMP_STEPS = 20
      private const val RAMP_INTERVAL_MILLIS = 50L
    }
  }

internal fun computeFadeVolume(
  remainingSeconds: Long,
  fadeSeconds: Int,
  originalVolume: Float,
): Float =
  if (fadeSeconds <= 0) {
    originalVolume
  } else {
    (originalVolume * remainingSeconds.toFloat() / fadeSeconds.toFloat()).coerceIn(0f, 1f)
  }
