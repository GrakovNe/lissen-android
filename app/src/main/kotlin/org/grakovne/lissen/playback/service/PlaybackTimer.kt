package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
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
    private var timer: Countdown? = null

    // the Android countdown; a test replaces it, it does not run on the JVM
    @VisibleForTesting
    internal var countdownFactory =
      CountdownFactory { totalMillis, intervalMillis, onTickSeconds, onFinished ->
        SuspendableCountDownTimer(totalMillis, intervalMillis, onTickSeconds, onFinished).also { it.start() }
      }

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

        // the countdown runs on the wall clock from a position read a little earlier, so playback
        // may reach the end of the episode first: running on into the next one is the expiry
        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) {
          if (timer == null || option != CurrentEpisodeTimerOption) return
          if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
          if (newPosition.mediaItemIndex == oldPosition.mediaItemIndex) return

          expire()
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
      if (totalMillis <= 0L) {
        // nothing left to wait for: expire right away rather than leave a timer that never fires
        this.option = option
        expire()
        return
      }

      broadcastRemaining(delayInSeconds.toLong())

      timer = countdownFactory.create(totalMillis, 500L, { seconds -> broadcastRemaining(seconds) }, { expire() })

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      this.option = option
      if (exoPlayer.isPlaying.not() && option == CurrentEpisodeTimerOption) {
        timer?.pause()
      }
    }

    /** An armed countdown to the end of the episode: while it runs, the end of the episode is its to take. */
    val isEpisodeTimerRunning: Boolean
      get() = timer != null && option == CurrentEpisodeTimerOption

    private fun expire() {
      Timber.d("Timer expired, pausing and broadcasting")
      // an expiry is not a cancellation: no TimerCancelled, or the fade would revert at the pause.
      // Stopped, since playback may have run out ahead of it and it would finish a second time
      timer?.stop()
      timer = null
      // paused here and now, not by whoever picks the event up later: anything that watches the
      // player and would act on this very moment sees it paused already
      exoPlayer.pause()
      playbackEventBus.emit(PlaybackEvent.TimerExpired)
      stopTimer()
    }

    private fun broadcastRemaining(seconds: Long) {
      playbackEventBus.emit(PlaybackEvent.TimerTick(seconds))
    }

    fun stopTimer() {
      Timber.d("Stopping timer")
      timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
      timer?.stop()
      timer = null

      exoPlayer.removeListener(playerListener)
    }
  }
