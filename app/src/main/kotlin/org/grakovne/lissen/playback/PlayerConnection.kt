package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException

/**
 * Until the session is bound every query answers a neutral value and every command is
 * dropped: there is nothing to play, pause or seek in before a queue exists. A command that
 * must survive the wait goes through [whenConnected].
 */
interface PlayerConnection {
  val isConnected: Boolean
  val isPlaying: Boolean
  val currentMediaItemIndex: Int
  val currentPositionMs: Long

  fun connect(
    listener: Listener,
    onConnected: () -> Unit,
  )

  fun whenConnected(action: () -> Unit)

  fun play(speed: Float)

  fun pause()

  fun seekTo(
    mediaItemIndex: Int,
    positionMs: Long,
  )

  fun setPlaybackSpeed(speed: Float)

  fun clear()

  fun release()

  interface Listener {
    fun onIsPlayingChanged(isPlaying: Boolean)

    fun onPositionDiscontinuity()

    fun onEnded()

    fun onError(error: PlaybackException)
  }
}
