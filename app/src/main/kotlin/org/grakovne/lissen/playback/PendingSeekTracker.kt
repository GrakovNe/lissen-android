package org.grakovne.lissen.playback

import kotlin.math.abs

internal class PendingSeekTracker(
  private val toleranceSeconds: Double = TOLERANCE_SECONDS,
  private val maxTicks: Int = MAX_TICKS,
) {
  private var target: Double? = null
  private var ticks = 0

  fun onSeekRequested(position: Double) {
    target = position
    ticks = 0
  }

  fun baseFor(currentPosition: Double): Double = target ?: currentPosition

  fun onPositionUpdate(position: Double) {
    val current = target ?: return

    when {
      abs(position - current) <= toleranceSeconds -> target = null
      ++ticks >= maxTicks -> target = null
    }
  }

  private companion object {
    private const val TOLERANCE_SECONDS = 2.0
    private const val MAX_TICKS = 6
  }
}
