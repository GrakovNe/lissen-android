package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.PlaybackSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlaybackSessionAdoptionTest {
  @Test
  fun `failed retry keeps the existing local session`() {
    val previous = PlaybackSession.local("book")
    val newlyOpened = PlaybackSession.local("book")

    val adoption = choosePlaybackSession(previous, newlyOpened, itemId = "book", keepLocal = true)

    assertEquals(previous, adoption.session)
    assertNull(adoption.completedOfflineSessionId)
  }

  @Test
  fun `remote recovery completes the previous local session`() {
    val previous = PlaybackSession.local("book")
    val remote = PlaybackSession.remote("remote", "book")

    val adoption = choosePlaybackSession(previous, remote, itemId = "book", keepLocal = true)

    assertEquals(remote, adoption.session)
    assertEquals(previous.sessionId, adoption.completedOfflineSessionId)
  }

  @Test
  fun `chapter transition adopts a fresh local session`() {
    val previous = PlaybackSession.local("book")
    val nextChapter = PlaybackSession.local("book")

    val adoption = choosePlaybackSession(previous, nextChapter, itemId = "book", keepLocal = false)

    assertEquals(nextChapter, adoption.session)
    assertNull(adoption.completedOfflineSessionId)
  }
}
