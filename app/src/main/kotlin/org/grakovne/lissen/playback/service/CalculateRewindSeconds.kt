package org.grakovne.lissen.playback.service

import kotlin.math.roundToLong

private const val MIN_REWIND_SECONDS = 5
private const val FULL_REWIND_WINDOW_MILLIS = 300_000L

fun calculateRewindSeconds(
  settingSeconds: Int,
  pausedMillis: Long,
): Double {
  // Interpolate from the five second floor up to the setting across the
  // 300 second pause window: short pauses still get the floor, longer ones
  // approach the full configured amount.
  val extraSeconds = (settingSeconds - MIN_REWIND_SECONDS).coerceAtLeast(0)
  val windowProgress =
    pausedMillis.coerceIn(0, FULL_REWIND_WINDOW_MILLIS).toDouble() /
      FULL_REWIND_WINDOW_MILLIS.toDouble()
  return MIN_REWIND_SECONDS + extraSeconds * windowProgress
}

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
