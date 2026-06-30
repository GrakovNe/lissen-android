package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.domain.PlaybackSessionSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackSessionResponseConverterTest {
  private val converter = PlaybackSessionResponseConverter()

  @Test
  fun `maps response fields and marks the session as remote`() {
    val result = converter.apply(PlaybackSessionResponse(id = "session-1", libraryItemId = "item-1"))

    assertEquals("session-1", result.sessionId)
    assertEquals("item-1", result.itemId)
    assertEquals(PlaybackSessionSource.REMOTE, result.sessionSource)
  }
}
