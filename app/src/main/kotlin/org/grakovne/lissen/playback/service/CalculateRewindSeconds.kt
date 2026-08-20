package org.grakovne.lissen.playback.service

import kotlin.math.roundToLong

private const val FULL_REWIND_WINDOW_MILLIS = 300_000L

fun calculateRewindSeconds(
  settingSeconds: Int,
  pausedMillis: Long,
): Double = settingSeconds * pausedMillis.coerceIn(0, FULL_REWIND_WINDOW_MILLIS).toDouble() / FULL_REWIND_WINDOW_MILLIS.toDouble()

data class RewindTarget(
  val chapterIndex: Int,
  val positionMillis: Long,
)

fun calculateRewindTarget(
  chapterIndex: Int,
  positionMillis: Long,
  chapterDurationsMillis: List<Long>,
  rewindSeconds: Double,
): RewindTarget {
  val rewindMillis = (rewindSeconds * 1000).roundToLong()
  if (rewindMillis <= 0 || chapterDurationsMillis.isEmpty()) {
    return RewindTarget(chapterIndex, positionMillis)
  }

  var targetIndex = chapterIndex
  var targetPosition = positionMillis
  var remainingRewind = rewindMillis

  while (remainingRewind > 0) {
    if (targetPosition >= remainingRewind) {
      targetPosition -= remainingRewind
      remainingRewind = 0
    } else {
      remainingRewind -= targetPosition
      if (targetIndex == 0) {
        return RewindTarget(0, 0)
      }
      targetIndex -= 1
      targetPosition = chapterDurationsMillis[targetIndex]
    }
  }

  return RewindTarget(targetIndex, targetPosition)
}
