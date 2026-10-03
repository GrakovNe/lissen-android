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

        // the countdown was armed from a position polled a moment earlier, so it can be behind
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
      // before the expiry below, which reads it
      this.option = option

      val totalMillis = (delayInSeconds * 1000).toLong()
      if (totalMillis <= 0L) {
        expire()
        return
      }

      broadcastRemaining(delayInSeconds.toLong())

      timer = countdownFactory.create(totalMillis, 500L, { seconds -> broadcastRemaining(seconds) }, { expire() })

      exoPlayer.removeListener(playerListener)
      exoPlayer.addListener(playerListener)

      if (exoPlayer.isPlaying.not() && option == CurrentEpisodeTimerOption) {
        timer?.pause()
      }
    }

    val isEpisodeTimerRunning: Boolean
      get() = timer != null && option == CurrentEpisodeTimerOption

    /** True from the pause of an expiring "end of episode" timer until every listener has seen that pause. */
    var isEpisodeTimerExpiring: Boolean = false
      private set

    // cleared on onEvents, which comes after every listener saw onPlayWhenReadyChanged: a pause
    // made inside a player callback is delivered only after pause() has returned
    private val expiryListener =
      object : Player.Listener {
        override fun onEvents(
          player: Player,
          events: Player.Events,
        ) {
          if (!events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) return

          isEpisodeTimerExpiring = false
          exoPlayer.removeListener(this)
        }
      }

    private fun expire() {
      Timber.d("Timer expired, pausing and broadcasting")
      // an expiry is not a cancellation: no TimerCancelled event, or the fade would undo itself at the pause
      timer?.stop()
      timer = null

      // only a player that is going to deliver the pause, or nothing would clear the flag
      if (option == CurrentEpisodeTimerOption && exoPlayer.playWhenReady) {
        isEpisodeTimerExpiring = true
        exoPlayer.addListener(expiryListener)
      }

      // pause before the event: auto-skip must see the player paused at this exact moment
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
