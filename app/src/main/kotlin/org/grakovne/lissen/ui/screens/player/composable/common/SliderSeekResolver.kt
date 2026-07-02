package org.grakovne.lissen.ui.screens.player.composable.common

import kotlin.math.abs

internal data class PendingSeek(
  val target: Double,
  val ticks: Int = 0,
)

internal const val SEEK_TOLERANCE_SECONDS = 2.0
internal const val MAX_PENDING_SEEK_TICKS = 6

internal fun resolveSliderPosition(
  incoming: Double,
  pending: PendingSeek?,
  toleranceSeconds: Double = SEEK_TOLERANCE_SECONDS,
  maxTicks: Int = MAX_PENDING_SEEK_TICKS,
): Pair<Double, PendingSeek?> =
  when {
    pending == null -> incoming to null
    abs(incoming - pending.target) <= toleranceSeconds -> incoming to null
    pending.ticks >= maxTicks -> incoming to null
    else -> pending.target to pending.copy(ticks = pending.ticks + 1)
  }
