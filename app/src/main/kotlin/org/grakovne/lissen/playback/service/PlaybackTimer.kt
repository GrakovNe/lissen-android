package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.playback.PlaybackEvent
import org.grakovne.lissen.playback.PlaybackEventBus
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimer
  @Inject
  constructor(
    private val playbackEventBus: PlaybackEventBus,
    private val exoPlayer: ExoPlayer,
  ) {
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
            playbackEventBus.emit(PlaybackEvent.TimerExpired)
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

    private fun broadcastRemaining(seconds: Long) {
      playbackEventBus.emit(PlaybackEvent.TimerTick(seconds))
    }

    fun stopTimer() {
      Timber.d("Stopping timer")
      timer?.cancel()
      timer = null

      exoPlayer.removeListener(playerListener)
    }
  }
