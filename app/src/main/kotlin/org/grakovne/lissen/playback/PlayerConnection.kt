package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException

/**
 * The player behind the media session, reduced to what [MediaRepository] asks of it. Every
 * query answers a neutral value until the session is bound, and every command is either
 * queued until then ([play]) or dropped ([pause], [seekTo], [setPlaybackSpeed], [clear]):
 * there is nothing to pause or seek in before a queue exists.
 */
interface PlayerConnection {
  val isConnected: Boolean
  val isPlaying: Boolean
  val currentMediaItemIndex: Int
  val currentPositionMs: Long

  /** Binds the session once; [listener] hears the player and [onConnected] runs when it is bound. */
  fun connect(
    listener: Listener,
    onConnected: () -> Unit,
  )

  /** Runs [action] at once when the session is bound, otherwise once it binds. */
  fun whenConnected(action: () -> Unit)

  /** Prepares the queue and starts playing it at [speed]. */
  fun play(speed: Float)

  fun pause()

  fun seekTo(
    mediaItemIndex: Int,
    positionMs: Long,
  )

  fun setPlaybackSpeed(speed: Float)

  /** Stops playback and drops the queue. */
  fun clear()

  /** Drops the session binding, see [MediaRepository.release]. */
  fun release()

  interface Listener {
    fun onIsPlayingChanged(isPlaying: Boolean)

    fun onPositionDiscontinuity()

    fun onEnded()

    fun onError(error: PlaybackException)
  }
}
