package org.grakovne.lissen.channel.audiobookshelf.podcast

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastOrderingRequestConverter
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastAudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()
  private val preferences = mockk<LibraryPreferences>()

  private val channel =
    PodcastAudiobookshelfChannel(
      hostProvider = mockk(relaxed = true),
      dataRepository = repository,
      recentListeningResponseConverter = mockk(relaxed = true),
      preferences = preferences,
      syncService = mockk(relaxed = true),
      sessionResponseConverter = mockk(relaxed = true),
      libraryListResponseConverter = LibraryListResponseConverter(),
      libraryResponseConverter = mockk(relaxed = true),
      connectionInfoResponseConverter = mockk(relaxed = true),
      bookmarksResponseConverter = mockk(relaxed = true),
      bookmarkItemResponseConverter = mockk(relaxed = true),
      podcastOrderingRequestConverter = PodcastOrderingRequestConverter(),
      podcastPageResponseConverter = mockk(relaxed = true),
      podcastResponseConverter = mockk(relaxed = true),
      podcastSearchItemsConverter = mockk(relaxed = true),
    )

  @Test
  fun `fetchBooks forwards an encoded podcast filter`() =
    runBlocking {
      every { preferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
      coEvery {
        repository.fetchPodcastItems(
          libraryId = "podcasts",
          pageSize = 20,
          pageNumber = 1,
          sort = "media.metadata.title",
          direction = "0",
          filter = "genres.TmV3cw==",
        )
      } returns OperationResult.Error(OperationError.NetworkError)

      val result = channel.fetchBooks("podcasts", 20, 1, "genres" to "News")

      assertEquals(OperationError.NetworkError, (result as OperationResult.Error).code)
      coVerify(exactly = 1) {
        repository.fetchPodcastItems(
          libraryId = "podcasts",
          pageSize = 20,
          pageNumber = 1,
          sort = "media.metadata.title",
          direction = "0",
          filter = "genres.TmV3cw==",
        )
      }
    }
}
