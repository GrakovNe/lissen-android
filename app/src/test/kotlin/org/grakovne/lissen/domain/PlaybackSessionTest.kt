package org.grakovne.lissen.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackSessionTest {
  @Test
  fun `local creates a session with a generated id prefixed local- and LOCAL source`() {
    val session = PlaybackSession.local("item-1")

    assertTrue(session.sessionId.startsWith("local-"))
    assertEquals("item-1", session.itemId)
    assertEquals(PlaybackSessionSource.LOCAL, session.sessionSource)
  }

  @Test
  fun `local generates a different session id on each call`() {
    val first = PlaybackSession.local("item-1")
    val second = PlaybackSession.local("item-1")

    assertNotEquals(first.sessionId, second.sessionId)
  }

  @Test
  fun `remote uses the given session id and REMOTE source`() {
    val session = PlaybackSession.remote(sessionId = "session-1", itemId = "item-1")

    assertEquals("session-1", session.sessionId)
    assertEquals("item-1", session.itemId)
    assertEquals(PlaybackSessionSource.REMOTE, session.sessionSource)
  }
}
