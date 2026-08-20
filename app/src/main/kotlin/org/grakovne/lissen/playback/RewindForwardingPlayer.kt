package org.grakovne.lissen.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

internal class RewindForwardingPlayer(
  player: Player,
  private val onPlay: (Player) -> Unit,
) : ForwardingPlayer(player) {
  override fun play() {
    // Player.play() sets playWhenReady to true synchronously, so only a real
    // false -> true transition applies the rewind. A redundant play() while
    // already playing must not rewind.
    if (!playWhenReady) {
      onPlay(this)
    }
    super.play()
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) {
    // Some controllers resume playback with setPlayWhenReady(true) instead of
    // play(). ForwardingPlayer.play() delegates straight to the wrapped
    // player's own play(), so these two overrides never trigger each other.
    if (playWhenReady && !this.playWhenReady) {
      onPlay(this)
    }
    super.setPlayWhenReady(playWhenReady)
  }
}
