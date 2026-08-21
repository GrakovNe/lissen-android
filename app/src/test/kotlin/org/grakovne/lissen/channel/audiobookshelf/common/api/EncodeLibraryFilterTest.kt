package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EncodeLibraryFilterTest {
  @Test
  fun `encodes filter value as unwrapped UTF-8 base64`() {
    assertEquals("genres.U2NpZW5jZSBGaWN0aW9u", encodeLibraryFilter("genres", "Science Fiction"))
  }
}
