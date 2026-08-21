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
  rewindSeconds: Double,
): RewindTarget {
  val rewindMillis = (rewindSeconds * 1000).roundToLong()
  if (rewindMillis <= 0) {
    return RewindTarget(chapterIndex, positionMillis)
  }

  // Clamp inside the current chapter: the timeline holds one media item per
  // chapter, and PlaybackNavigationService.onPositionDiscontinuity treats a
  // backward seek into a different media item as a discontinuity. With a
  // partly downloaded book a rewind that walks into a missing chapter turns
  // into a jump to the start of a much earlier chapter, or a stall, so a
  // rewind must never cross a chapter boundary.
  return RewindTarget(chapterIndex, (positionMillis - rewindMillis).coerceAtLeast(0))
}
