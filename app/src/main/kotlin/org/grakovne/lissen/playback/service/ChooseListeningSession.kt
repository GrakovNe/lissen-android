package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

fun chooseListeningSession(
  previous: ListeningSession?,
  itemId: String,
  chapterId: String?,
  progress: PlaybackProgress,
  now: Long,
): ListeningSession =
  previous
    ?.takeIf { it.itemId == itemId }
    ?.takeIf { it.chapterId == chapterId }
    ?.takeIf { now - it.updatedAt < LISTENING_IDLE_GAP_MS }
    ?.takeIf { sameCalendarDay(it.updatedAt, now) }
    ?: ListeningSession(
      id = UUID.randomUUID().toString(),
      itemId = itemId,
      chapterId = chapterId,
      startedAt = now,
      updatedAt = now,
      startTime = progress.currentTotalTime,
      timeListeningMs = 0,
      progress = progress,
    )

internal fun sameCalendarDay(
  left: Long,
  right: Long,
): Boolean =
  Instant.ofEpochMilli(left).atZone(ZoneId.systemDefault()).toLocalDate() ==
    Instant.ofEpochMilli(right).atZone(ZoneId.systemDefault()).toLocalDate()

internal const val LISTENING_IDLE_GAP_MS = 4 * 60 * 60 * 1000L
