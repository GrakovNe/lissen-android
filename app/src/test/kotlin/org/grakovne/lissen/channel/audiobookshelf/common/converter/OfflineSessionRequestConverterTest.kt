package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.OfflineSessionOwner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OfflineSessionRequestConverterTest {
  private val converter = OfflineSessionRequestConverter()
  private val deviceInfo = DeviceInfo(clientName = "Lissen", deviceId = "dev", deviceName = "Lissen")

  private fun session(libraryType: LibraryType) =
    OfflineSession(
      id = "s1",
      owner = OfflineSessionOwner("https://abs.example", "reader"),
      libraryItemId = "item",
      episodeId = "ep".takeIf { libraryType == LibraryType.PODCAST },
      libraryId = "lib",
      libraryType = libraryType,
      displayTitle = "Dune",
      displayAuthor = "Frank Herbert",
      duration = 300.0,
      startTime = 10.0,
      currentTime = 55.0,
      timeListening = 45.0,
      startedAt = 1_000L,
      updatedAt = 1_726_000_000_000L,
    )

  @Test
  fun `book session maps to a local play of a book`() {
    val request = converter.apply(session(LibraryType.LIBRARY), deviceInfo, "Lissen App")

    assertEquals("s1", request.id)
    assertEquals("item", request.libraryItemId)
    assertNull(request.episodeId)
    assertEquals("book", request.mediaType)
    assertEquals(3, request.playMethod)
    assertEquals("Lissen App", request.mediaPlayer)
    assertEquals(deviceInfo, request.deviceInfo)
    assertEquals(300.0, request.duration)
    assertEquals(10.0, request.startTime)
    assertEquals(55.0, request.currentTime)
    assertEquals(45.0, request.timeListening)
    assertEquals(1_000L, request.startedAt)
    assertEquals(1_726_000_000_000L, request.updatedAt)
  }

  @Test
  fun `podcast session keeps the episode and maps to podcast media type`() {
    val request = converter.apply(session(LibraryType.PODCAST), deviceInfo, "Lissen App")

    assertEquals("ep", request.episodeId)
    assertEquals("podcast", request.mediaType)
  }

  @Test
  fun `date and day of week are derived from the last update in the local zone`() {
    val request = converter.apply(session(LibraryType.LIBRARY), deviceInfo, "Lissen App")
    val updatedAt = Instant.ofEpochMilli(1_726_000_000_000L).atZone(ZoneId.systemDefault())

    assertEquals(updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE), request.date)
    assertTrue(request.dayOfWeek.isNotBlank())
  }
}
