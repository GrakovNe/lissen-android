package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AccumulateListeningTest {
  private val session =
    ListeningSession(
      id = "session",
      itemId = "book",
      chapterId = "chapter",
      startedAt = 1_000,
      updatedAt = 1_000,
      startTime = 100.0,
      timeListeningMs = 60_500,
      progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 110.0),
    )

  @Test
  fun `played time is added to the accumulated total`() {
    val progress = PlaybackProgress(currentChapterTime = 55.0, currentTotalTime = 155.0)

    val accumulated = accumulateListening(session, playedMs = 45_250, progress = progress, now = 46_250)

    assertEquals(105_750, accumulated.timeListeningMs)
    assertEquals(progress, accumulated.progress)
    assertEquals(46_250, accumulated.updatedAt)
  }

  @Test
  fun `zero played time keeps the total and refreshes progress`() {
    val progress = PlaybackProgress(currentChapterTime = 12.0, currentTotalTime = 112.0)

    val accumulated = accumulateListening(session, playedMs = 0, progress = progress, now = 2_000)

    assertEquals(60_500, accumulated.timeListeningMs)
    assertEquals(progress, accumulated.progress)
  }

  @Test
  fun `negative played time is ignored`() {
    val accumulated = accumulateListening(session, playedMs = -5_000, progress = session.progress, now = 2_000)

    assertEquals(60_500, accumulated.timeListeningMs)
  }

  @Test
  fun `session identity and start are preserved`() {
    val accumulated = accumulateListening(session, playedMs = 1_000, progress = session.progress, now = 2_000)

    assertEquals(session.id, accumulated.id)
    assertEquals(session.startedAt, accumulated.startedAt)
    assertEquals(session.startTime, accumulated.startTime)
  }
}
