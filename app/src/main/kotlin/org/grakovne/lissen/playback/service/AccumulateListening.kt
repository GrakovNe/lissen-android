package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress

fun accumulateListening(
  session: ListeningSession,
  playedMs: Long,
  progress: PlaybackProgress,
  now: Long,
): ListeningSession =
  session.copy(
    timeListeningMs = session.timeListeningMs + playedMs.coerceAtLeast(0),
    progress = progress,
    updatedAt = now,
  )
