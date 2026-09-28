package org.grakovne.lissen.playback.autoskip

import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The "forward" step is the player's own movement, not the listener's scrubbing: whatever it
 * lands in is skipped like anything playback reaches by itself. The step is marked here with
 * its target right before its seek and taken by the discontinuity that lands there, both on the
 * main thread. A mark whose seek never happened, or was overtaken by another, matches nothing
 * and is dropped by the next seek. Only forward: a step back into an intro has to be able to
 * land there.
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

    /** Whether [landing] is the step that was expected; the expectation is spent either way. */
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
