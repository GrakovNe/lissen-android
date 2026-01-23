package org.grakovne.lissen.playback.service

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.lib.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.lib.domain.TimerOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimer
  @Inject
  constructor(
    @ApplicationContext private val applicationContext: Context,
    private val exoPlayer: ExoPlayer,
  ) {
    private val localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext)

    private var option: TimerOption? = null
    private var timer: SuspendableCountDownTimer? = null

    private val playerListener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          val currentTimer = timer ?: return

          when (isPlaying) {
            true -> timer = currentTimer.resume()
            false -> currentTimer.pause()
          }
        }
      }

    @OptIn(UnstableApi::class)
    fun startTimer(
      delayInSeconds: Double,
      option: TimerOption,
    ) {
      stopTimer()
      exoPlayer.volume = 1f

      val totalMillis = (delayInSeconds * 1000).toLong()
      if (totalMillis <= 0L) return

      broadcastRemaining(delayInSeconds.toLong())

      timer =
        SuspendableCountDownTimer(
          totalMillis = totalMillis,
          intervalMillis = 500L,
          onTickSeconds = { seconds ->
            broadcastRemaining(seconds)
            handleVolumeFade(seconds, delayInSeconds)
          },
          onFinished = {
            localBroadcastManager.sendBroadcast(Intent(PlaybackService.TIMER_EXPIRED))
            stopTimer()
          },
        ).also { it.start() }

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      this.option = option
      if (exoPlayer.isPlaying.not()) {
        timer?.pause()
      }
    }

    @OptIn(UnstableApi::class)
    private fun broadcastRemaining(seconds: Long) {
      localBroadcastManager.sendBroadcast(
        Intent(PlaybackService.TIMER_TICK)
          .putExtra(PlaybackService.TIMER_REMAINING, seconds),
      )
    }

    fun stopTimer() {
      timer?.cancel()
      timer = null
      exoPlayer.volume = 1f

      exoPlayer.removeListener(playerListener)
    }

    private fun handleVolumeFade(
      remainingSeconds: Long,
      totalSeconds: Double,
    ) {
      val fadeWindow = 60.0 // Fade out over last 60 seconds

      // If remaining is large, max volume
      if (remainingSeconds > fadeWindow) {
        if (exoPlayer.volume != 1f) exoPlayer.volume = 1f
        return
      }

      // Calculate progress 0.0 (end) to 1.0 (start of fade)
      // If total duration is very short (e.g. 30s), fade over the whole duration?
      // Let's stick to max 60s fade.

      val progress = (remainingSeconds / fadeWindow).toFloat().coerceIn(0f, 1f)

      // Use a simple linear fade or squared for smoother perception
      exoPlayer.volume = progress
    }
  }
