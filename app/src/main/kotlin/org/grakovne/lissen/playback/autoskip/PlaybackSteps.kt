package org.grakovne.lissen.playback.autoskip

import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "forward" step is the player's own movement, so what it lands in is skipped. It is marked
 * with its target before its seek and matched by the discontinuity landing there; any other seek
 * drops the mark. Forward only: a step back into an intro has to be able to land there.
 */
@Singleton
class PlaybackSteps
  @Inject
  constructor() {
    private var expected: Target? = null

    fun expect(
      mediaItemIndex: Int,
      positionMs: Long,
    ) {
      expected = Target(mediaItemIndex, positionMs)
    }

    fun take(landing: Player.PositionInfo): Boolean {
      val target = expected ?: return false
      expected = null

      return target == Target(landing.mediaItemIndex, landing.positionMs)
    }

    private data class Target(
      val mediaItemIndex: Int,
      val positionMs: Long,
    )
  }
