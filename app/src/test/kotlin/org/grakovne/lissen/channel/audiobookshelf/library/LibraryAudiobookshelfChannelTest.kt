package org.grakovne.lissen.channel.audiobookshelf.library

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibraryOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.library.converter.LibrarySearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryGenreItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryGenresResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryMetadata
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryNarratorItem
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryNarratorsResponse
import org.grakovne.lissen.channel.audiobookshelf.library.model.Media
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LibraryAudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()
  private val preferences =
    mockk<LibraryPreferences>(relaxed = true) {
      every { getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    }

  private val channel =
    LibraryAudiobookshelfChannel(
      hostProvider = mockk(relaxed = true),
      repository = repository,
      recentListeningResponseConverter = mockk(relaxed = true),
      preferences = preferences,
      syncService = mockk(relaxed = true),
      sessionResponseConverter = mockk(relaxed = true),
      libraryResponseConverter = mockk(relaxed = true),
      connectionInfoResponseConverter = mockk(relaxed = true),
      bookmarksResponseConverter = mockk(relaxed = true),
      bookmarkItemResponseConverter = mockk(relaxed = true),
      offlineSessionRequestConverter = mockk(relaxed = true),
      localSessionSyncResponseConverter = mockk(relaxed = true),
      libraryOrderingRequestConverter = LibraryOrderingRequestConverter(),
      libraryFilteringRequestConverter = mockk(relaxed = true),
      libraryPageResponseConverter = LibraryPageResponseConverter(),
      libraryAuthorsResponseConverter = mockk(relaxed = true),
      bookResponseConverter = mockk(relaxed = true),
      librarySearchItemsConverter = LibrarySearchItemsConverter(),
      libraryListResponseConverter = LibraryListResponseConverter(),
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

  @Test
  fun `genre grouping sorts genres by name and pages them locally`() =
    runBlocking {
      coEvery { repository.fetchLibraryGenres(LIBRARY) } returns
        OperationResult.Success(
          LibraryGenresResponse(
            genresWithCount =
              listOf(
                LibraryGenreItem("history", 7),
                LibraryGenreItem("Fantasy", 6),
                LibraryGenreItem("Romance", null),
              ),
          ),
        )

      val first = channel.fetchLibrary(LIBRARY, pageSize = 2, pageNumber = 0, LibraryGrouping.GENRE) as OperationResult.Success
      assertEquals(
        listOf(LibraryEntry.GenreEntry("Fantasy", 6), LibraryEntry.GenreEntry("history", 7)),
        first.data.items,
      )
      assertEquals(3, first.data.totalItems)

      val second = channel.fetchLibrary(LIBRARY, pageSize = 2, pageNumber = 1, LibraryGrouping.GENRE) as OperationResult.Success
      assertEquals(listOf(LibraryEntry.GenreEntry("Romance", 0)), second.data.items)
    }

  @Test
  fun `narrator grouping takes names and counts from the narrators endpoint`() =
    runBlocking {
      coEvery { repository.fetchLibraryNarrators(LIBRARY) } returns
        OperationResult.Success(
          LibraryNarratorsResponse(
            narrators = listOf(LibraryNarratorItem("Stephen Fry", 6), LibraryNarratorItem("Jim Dale", 7)),
          ),
        )

      val result = channel.fetchLibrary(LIBRARY, pageSize = 20, pageNumber = 0, LibraryGrouping.NARRATOR) as OperationResult.Success
      assertEquals(
        listOf(LibraryEntry.NarratorEntry("Jim Dale", 7), LibraryEntry.NarratorEntry("Stephen Fry", 6)),
        result.data.items,
      )
    }

  @Test
  fun `fetchGenreBooks filters by the base64 genre and collects every page`() =
    runBlocking {
      val filter = "genres.0JTQtdGC0LXQutGC0LjQsg=="
      coEvery { repository.fetchLibraryItems(LIBRARY, any(), 0, any(), any(), filter) } returns page((1..100).map { "b$it" }, total = 130)
      coEvery { repository.fetchLibraryItems(LIBRARY, any(), 1, any(), any(), filter) } returns page((101..130).map { "b$it" }, total = 130)

      val result = channel.fetchGenreBooks(LIBRARY, "Детектив") as OperationResult.Success

      assertEquals((1..130).map { "b$it" }, result.data.map { it.id })
      coVerify(exactly = 0) { repository.fetchLibraryItems(LIBRARY, any(), 2, any(), any(), any()) }
    }

  @Test
  fun `fetchNarratorBooks filters by the base64 narrator`() =
    runBlocking {
      val filter = "narrators.U3RlcGhlbiBGcnk="
      coEvery { repository.fetchLibraryItems(LIBRARY, any(), 0, any(), any(), filter) } returns page(listOf("b1"), total = 1)

      val result = channel.fetchNarratorBooks(LIBRARY, "Stephen Fry") as OperationResult.Success

      assertEquals(listOf("b1"), result.data.map { it.id })
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
