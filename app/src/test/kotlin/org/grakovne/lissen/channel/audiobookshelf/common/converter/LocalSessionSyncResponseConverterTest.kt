package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionSyncResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionSyncResultResponse
import org.grakovne.lissen.domain.OfflineSessionSyncResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalSessionSyncResponseConverterTest {
  private val converter = LocalSessionSyncResponseConverter()

  @Test
  fun `results are mapped one to one and keep their order`() {
    val results =
      converter.apply(
        LocalSessionSyncResponse(
          results =
            listOf(
              LocalSessionSyncResultResponse(id = "a", success = true),
              LocalSessionSyncResultResponse(id = "b", success = false, error = "Media item not found"),
            ),
        ),
      )

    assertEquals(
      listOf(
        OfflineSessionSyncResult(id = "a", success = true, error = null),
        OfflineSessionSyncResult(id = "b", success = false, error = "Media item not found"),
      ),
      results,
    )
  }

  @Test
  fun `empty response maps to no results`() {
    assertTrue(converter.apply(LocalSessionSyncResponse(results = emptyList())).isEmpty())
  }
}
