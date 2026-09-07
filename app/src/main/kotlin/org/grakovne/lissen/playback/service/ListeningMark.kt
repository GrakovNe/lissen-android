package org.grakovne.lissen.playback.service

import androidx.annotation.Keep

@Keep
data class ListeningMark(
  val playingSince: Long?,
  val unsyncedMs: Long,
)

fun accumulateListening(
  mark: ListeningMark,
  isPlaying: Boolean,
  now: Long,
): ListeningMark =
  ListeningMark(
    playingSince = now.takeIf { isPlaying },
    unsyncedMs = mark.unsyncedMs + (mark.playingSince?.let { now - it } ?: 0),
  )
