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
    private var expiredAtNanos: Long? = null

    // the Android countdown and the clock; a test replaces them, neither runs on the JVM
    @VisibleForTesting
    internal var nanoTime: () -> Long = System::nanoTime

    @VisibleForTesting
    internal var countdownFactory =
      CountdownFactory { totalMillis, intervalMillis, onTickSeconds, onFinished ->
        SuspendableCountDownTimer(totalMillis, intervalMillis, onTickSeconds, onFinished).also { it.start() }
      }

    /** The pause an expiry asks for has happened: the timer no longer owns the end of the episode. */
    private val pauseWatcher =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
          if (isPlaying) return
          expiredAtNanos = null
          exoPlayer.removeListener(this)
        }
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

    /** An armed countdown to the end of the episode, which a skip that moves to another episode has to re-arm. */
    val isEpisodeTimerRunning: Boolean
      get() = timer != null && option == CurrentEpisodeTimerOption

    /**
     * The end of the episode came early (an outro is skipped): a timer waiting for it expires now.
     * True as well when it ran out by itself a moment ago and the pause is still on its way.
     */
    fun expireEpisodeTimer(): Boolean {
      val running = timer

      return when {
        option != CurrentEpisodeTimerOption -> {
          false
        }

        running == null -> {
          pauseStillOwned(expiredAtNanos, nanoTime())
        }

        else -> {
          running.stop()
          expire()
          true
        }
      }
    }

    private fun expire() {
      Timber.d("Timer expired, broadcasting")
      // an expiry is not a cancellation: no TimerCancelled, or the fade would revert at the pause
      timer = null
      playbackEventBus.emit(PlaybackEvent.TimerExpired)
      stopTimer()
      // after the stop, which forgets an earlier expiry: a cancel by the user closes the window too
      expiredAtNanos = nanoTime()
      exoPlayer.removeListener(pauseWatcher)
      exoPlayer.addListener(pauseWatcher)
    }

    private fun broadcastRemaining(seconds: Long) {
      playbackEventBus.emit(PlaybackEvent.TimerTick(seconds))
    }

    fun stopTimer() {
      Timber.d("Stopping timer")
      timer?.let { playbackEventBus.emit(PlaybackEvent.TimerCancelled) }
      timer?.stop()
      timer = null
      expiredAtNanos = null

      exoPlayer.removeListener(playerListener)
      exoPlayer.removeListener(pauseWatcher)
    }

    private companion object {
      /** How long after its own expiry the timer still owns the pause that follows. */
      const val PAUSE_ON_ITS_WAY_NANOS = 3_000_000_000L

      /** An expiry at [expiredAtNanos] still owns the pause on its way at [nowNanos]. */
      fun pauseStillOwned(
        expiredAtNanos: Long?,
        nowNanos: Long,
      ): Boolean = expiredAtNanos?.let { nowNanos - it < PAUSE_ON_ITS_WAY_NANOS } ?: false
    }
  }
