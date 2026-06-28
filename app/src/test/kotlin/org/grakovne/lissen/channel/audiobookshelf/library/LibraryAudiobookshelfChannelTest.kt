package org.grakovne.lissen.channel.audiobookshelf.library

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.Media
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LibraryAudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()

  private val channel =
    LibraryAudiobookshelfChannel(
      hostProvider = mockk(relaxed = true),
      repository = repository,
      recentListeningResponseConverter = mockk(relaxed = true),
      preferences = mockk(relaxed = true),
      syncService = mockk(relaxed = true),
      sessionResponseConverter = mockk(relaxed = true),
      libraryResponseConverter = mockk(relaxed = true),
      connectionInfoResponseConverter = mockk(relaxed = true),
      bookmarksResponseConverter = mockk(relaxed = true),
      bookmarkItemResponseConverter = mockk(relaxed = true),
      libraryOrderingRequestConverter = mockk(relaxed = true),
      libraryFilteringRequestConverter = mockk(relaxed = true),
      libraryPageResponseConverter = mockk(relaxed = true),
      bookResponseConverter = mockk(relaxed = true),
      librarySearchItemsConverter = LibrarySearchItemsConverter(),
    )

  @Test
  fun `fetchSeriesItems collects books across all pages and stops once total is reached`() =
    runBlocking {
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 45)
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns page((21..40).map { "b$it" }, total = 45)
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) } returns page((41..45).map { "b$it" }, total = 45)

      val result = channel.fetchSeriesItems(LIBRARY, SERIES)

      assertInstanceOf(OperationResult.Success::class.java, result)
      assertEquals((1..45).map { "b$it" }, (result as OperationResult.Success).data.map { it.id })

      coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) }
      coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) }
      coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
      coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 3) }
    }

  @Test
  fun `fetchSeriesItems fetches a single page when the series fits in one`() =
    runBlocking {
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page(listOf("b1", "b2", "b3"), total = 3)

      val result = channel.fetchSeriesItems(LIBRARY, SERIES) as OperationResult.Success
      assertEquals(listOf("b1", "b2", "b3"), result.data.map { it.id })

      coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), any()) }
    }

  @Test
  fun `fetchSeriesItems stops when a page comes back empty even if total is larger`() =
    runBlocking {
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 100)
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns page(emptyList(), total = 100)

      val result = channel.fetchSeriesItems(LIBRARY, SERIES) as OperationResult.Success
      assertEquals((1..20).map { "b$it" }, result.data.map { it.id })

      coVerify(exactly = 1) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) }
      coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
    }

  @Test
  fun `fetchSeriesItems propagates an error from a later page`() =
    runBlocking {
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 0) } returns page((1..20).map { "b$it" }, total = 45)
      coEvery { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 1) } returns OperationResult.Error(OperationError.NetworkError)

      val result = channel.fetchSeriesItems(LIBRARY, SERIES)

      assertInstanceOf(OperationResult.Error::class.java, result)
      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
      coVerify(exactly = 0) { repository.fetchSeriesItems(LIBRARY, SERIES, any(), 2) }
    }

  private fun page(
    ids: List<String>,
    total: Int,
  ): OperationResult<LibraryItemsResponse> =
    OperationResult.Success(
      LibraryItemsResponse(
        results = ids.map { item(it) },
        page = 0,
        total = total,
      ),
    )

  private fun item(id: String): LibraryItem =
    LibraryItem(
      id = id,
      media =
        Media(
          numChapters = null,
          metadata =
            LibraryMetadata(
              title = "Title $id",
              subtitle = null,
              seriesName = "Dune",
              authorName = "Frank Herbert",
            ),
        ),
    )

  companion object {
    private const val LIBRARY = "lib-1"
    private const val SERIES = "ser-1"
  }
}
