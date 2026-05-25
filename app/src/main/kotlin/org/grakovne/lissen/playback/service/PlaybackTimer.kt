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
import timber.log.Timber
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

          if (option == CurrentEpisodeTimerOption) {
            when (isPlaying) {
              true -> timer = currentTimer.resume()
              false -> currentTimer.pause()
            }
          }
        }
      }

    @OptIn(UnstableApi::class)
    fun startTimer(
      delayInSeconds: Double,
      option: TimerOption,
    ) {
      Timber.d("Starting timer: ${delayInSeconds.toInt()}s, option=$option")
      stopTimer()

      val totalMillis = (delayInSeconds * 1000).toLong()
      if (totalMillis <= 0L) return

      broadcastRemaining(delayInSeconds.toLong())

      timer =
        SuspendableCountDownTimer(
          totalMillis = totalMillis,
          intervalMillis = 500L,
          onTickSeconds = { seconds -> broadcastRemaining(seconds) },
          onFinished = {
            Timber.d("Timer expired, broadcasting")
            localBroadcastManager.sendBroadcast(Intent(PlaybackService.TIMER_EXPIRED))
            stopTimer()
          },
        ).also { it.start() }

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      this.option = option
      if (exoPlayer.isPlaying.not() && option == CurrentEpisodeTimerOption) {
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
      Timber.d("Stopping timer")
      timer?.cancel()
      timer = null

      exoPlayer.removeListener(playerListener)
    }
  }
