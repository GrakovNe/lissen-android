package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class ChooseListeningSessionTest {
  private val noon =
    LocalDate
      .of(2026, 8, 8)
      .atTime(12, 0)
      .atZone(ZoneId.systemDefault())
      .toInstant()
      .toEpochMilli()
  private val progress = PlaybackProgress(currentChapterTime = 10.0, currentTotalTime = 110.0)

  private val session =
    ListeningSession(
      id = "session",
      itemId = "book",
      chapterId = "chapter",
      startedAt = noon,
      updatedAt = noon,
      startTime = 100.0,
      timeListeningMs = 60_000,
      progress = progress,
    )

  @Test
  fun `same item and chapter continues the session`() {
    val chosen = chooseListeningSession(session, "book", "chapter", progress, noon + 45_000)

    assertSame(session, chosen)
  }

  @Test
  fun `missing previous session starts a new one`() {
    val chosen = chooseListeningSession(null, "book", "chapter", progress, noon)

    assertEquals("book", chosen.itemId)
    assertEquals("chapter", chosen.chapterId)
    assertEquals(0, chosen.timeListeningMs)
    assertEquals(progress.currentTotalTime, chosen.startTime)
  }

  @Test
  fun `item change starts a new session`() {
    val chosen = chooseListeningSession(session, "other", "chapter", progress, noon + 45_000)

    assertNotEquals(session.id, chosen.id)
    assertEquals("other", chosen.itemId)
  }

  @Test
  fun `chapter change starts a new session`() {
    val chosen = chooseListeningSession(session, "book", "other", progress, noon + 45_000)

    assertNotEquals(session.id, chosen.id)
    assertEquals("other", chosen.chapterId)
  }

  @Test
  fun `idle gap starts a new session`() {
    val chosen = chooseListeningSession(session, "book", "chapter", progress, noon + LISTENING_IDLE_GAP_MS)

    assertNotEquals(session.id, chosen.id)
  }

  @Test
  fun `calendar day change starts a new session`() {
    val nextDay = noon + 13 * 60 * 60 * 1000L

    val chosen = chooseListeningSession(session, "book", "chapter", progress, nextDay)

    assertNotEquals(session.id, chosen.id)
  }

  @Test
  fun `new session ids are unique`() {
    val first = chooseListeningSession(null, "book", "chapter", progress, noon)
    val second = chooseListeningSession(null, "book", "chapter", progress, noon)

    assertNotEquals(first.id, second.id)
  }
}
