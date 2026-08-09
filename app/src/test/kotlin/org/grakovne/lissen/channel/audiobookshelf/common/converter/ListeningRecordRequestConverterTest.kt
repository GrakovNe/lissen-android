package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.domain.ListeningMediaType
import org.grakovne.lissen.domain.ListeningRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListeningRecordRequestConverterTest {
  private val converter = ListeningRecordRequestConverter()

  private val record =
    ListeningRecord(
      id = "session",
      itemId = "item",
      episodeId = "episode",
      mediaType = ListeningMediaType.PODCAST,
      displayTitle = "Title",
      duration = 1705.06,
      startTime = 0.0,
      currentTime = 123.4,
      timeListeningMs = 61_500,
      startedAt = 1_000,
      updatedAt = 2_000,
    )

  @Test
  fun `listening time is converted to fractional seconds`() {
    val request = converter.apply(record, "device")

    assertEquals(61.5, request.timeListening)
  }

  @Test
  fun `podcast record maps to podcast media type with episode id`() {
    val request = converter.apply(record, "device")

    assertEquals("podcast", request.mediaType)
    assertEquals("episode", request.episodeId)
  }

  @Test
  fun `book record maps to book media type`() {
    val request = converter.apply(record.copy(mediaType = ListeningMediaType.BOOK, episodeId = null), "device")

    assertEquals("book", request.mediaType)
    assertEquals(null, request.episodeId)
  }

  @Test
  fun `request carries direct play method and device identity`() {
    val request = converter.apply(record, "device")

    assertEquals(DIRECT_PLAY_METHOD, request.playMethod)
    assertEquals("device", request.deviceInfo.deviceId)
    assertEquals("item", request.libraryItemId)
    assertEquals(123.4, request.currentTime)
    assertEquals(1_000, request.startedAt)
    assertEquals(2_000, request.updatedAt)
  }
}
