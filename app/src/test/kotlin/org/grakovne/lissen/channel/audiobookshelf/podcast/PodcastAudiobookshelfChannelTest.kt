package org.grakovne.lissen.channel.audiobookshelf.podcast

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LibraryListResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.PlaybackSessionResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.PlaybackStartRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.UserResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastOrderingRequestConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastPageResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.converter.PodcastSearchItemsConverter
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItem
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemMedia
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastMetadata
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchItemResponse
import org.grakovne.lissen.channel.audiobookshelf.podcast.model.PodcastSearchResponse
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PagedItems
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PodcastAudiobookshelfChannelTest {
  private val repository = mockk<AudioBookshelfRepository>()
  private val preferences = mockk<LibraryPreferences>(relaxed = true)
  private val podcastOrderingRequestConverter = mockk<PodcastOrderingRequestConverter>()
  private val podcastPageResponseConverter = mockk<PodcastPageResponseConverter>()
  private val podcastResponseConverter = mockk<PodcastResponseConverter>()
  private val podcastSearchItemsConverter = mockk<PodcastSearchItemsConverter>()
  private val sessionResponseConverter = mockk<PlaybackSessionResponseConverter>()

  private val channel =
    PodcastAudiobookshelfChannel(
      hostProvider = mockk(relaxed = true),
      dataRepository = repository,
      recentListeningResponseConverter = mockk(relaxed = true),
      preferences = preferences,
      syncService = mockk(relaxed = true),
      sessionResponseConverter = sessionResponseConverter,
      libraryListResponseConverter = LibraryListResponseConverter(),
      libraryResponseConverter = mockk(relaxed = true),
      connectionInfoResponseConverter = mockk(relaxed = true),
      bookmarksResponseConverter = mockk(relaxed = true),
      bookmarkItemResponseConverter = mockk(relaxed = true),
      podcastOrderingRequestConverter = podcastOrderingRequestConverter,
      podcastPageResponseConverter = podcastPageResponseConverter,
      podcastResponseConverter = podcastResponseConverter,
      podcastSearchItemsConverter = podcastSearchItemsConverter,
    )

  @Nested
  inner class FetchBooks {
    @Test
    fun `fetchBooks requests the configured ordering and converts the page`() =
      runBlocking {
        every { podcastOrderingRequestConverter.apply(any()) } returns ("title" to "0")
        val payload = PodcastItemsResponse(results = emptyList(), page = 0, total = 0)
        val converted = PagedItems<org.grakovne.lissen.domain.Book>(emptyList(), 0, 0)
        coEvery { repository.fetchPodcastItems(LIBRARY, 20, 1, "title", "0") } returns OperationResult.Success(payload)
        every { podcastPageResponseConverter.apply(payload) } returns converted

        val result = channel.fetchBooks(LIBRARY, pageSize = 20, pageNumber = 1, extraFilter = null)

        assertEquals(converted, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBooks propagates repository errors`() =
      runBlocking {
        every { podcastOrderingRequestConverter.apply(any()) } returns ("title" to "0")
        coEvery { repository.fetchPodcastItems(any(), any(), any(), any(), any()) } returns
          OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchBooks(LIBRARY, pageSize = 20, pageNumber = 0, extraFilter = null)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class Search {
    @Test
    fun `searchBooks converts the podcast search hits`() =
      runBlocking {
        val payload = PodcastSearchResponse(podcast = listOf(PodcastSearchItemResponse(podcastItem("p1"))))
        val converted = listOf(book("p1"))
        coEvery { repository.searchPodcasts(LIBRARY, "hub", 5) } returns OperationResult.Success(payload)
        every { podcastSearchItemsConverter.apply(listOf(podcastItem("p1"))) } returns converted

        val result = channel.searchBooks(LIBRARY, "hub", 5)

        assertEquals(converted, (result as OperationResult.Success).data)
      }

    @Test
    fun `searchBooks propagates the search error`() =
      runBlocking {
        coEvery { repository.searchPodcasts(LIBRARY, "hub", 5) } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.searchBooks(LIBRARY, "hub", 5)

        assertInstanceOf(OperationResult.Error::class.java, result)
      }
  }

  @Nested
  inner class Playback {
    @Test
    fun `startPlayback addresses the episode endpoint with the built request`() =
      runBlocking {
        val requestSlot = slot<PlaybackStartRequest>()
        val response = mockk<PlaybackSessionResponse>()
        val session = mockk<PlaybackSession>()
        coEvery { repository.startPodcastPlayback("pod-1", "ep-1", capture(requestSlot)) } returns OperationResult.Success(response)
        every { sessionResponseConverter.apply(response) } returns session

        val result = channel.startPlayback("pod-1", "ep-1", listOf("audio/mp4a-latm"), "device-1")

        assertEquals(session, (result as OperationResult.Success).data)
        assertEquals(listOf("audio/mp4a-latm"), requestSlot.captured.supportedMimeTypes)
        assertEquals("device-1", requestSlot.captured.deviceInfo.deviceId)
      }
  }

  @Nested
  inner class FetchBook {
    @Test
    fun `fetchBook passes only the episode progress of this podcast, newest first and deduplicated per episode`() =
      runBlocking {
        val podcast = mockk<PodcastResponse>()
        val detailed = mockk<DetailedItem>()
        val progress =
          listOf(
            mediaProgress("pod-1", episodeId = "ep-1", lastUpdate = 100),
            mediaProgress("pod-1", episodeId = "ep-1", lastUpdate = 300),
            mediaProgress("pod-1", episodeId = "ep-2", lastUpdate = 200),
            mediaProgress("pod-1", episodeId = null, lastUpdate = 400),
            mediaProgress("other", episodeId = "ep-3", lastUpdate = 500),
          )

        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Success(UserResponse(progress))
        coEvery { repository.fetchPodcastItem("pod-1") } returns OperationResult.Success(podcast)

        val progressSlot = slot<List<MediaProgressResponse>>()
        every { podcastResponseConverter.apply(eq(podcast), capture(progressSlot)) } returns detailed

        val result = channel.fetchBook("pod-1")

        assertEquals(detailed, (result as OperationResult.Success).data)
        assertEquals(listOf("ep-1", "ep-2"), progressSlot.captured.map { it.episodeId })
        assertEquals(listOf(300L, 200L), progressSlot.captured.map { it.lastUpdate })
      }

    @Test
    fun `fetchBook converts with empty progress when the user has no progress`() =
      runBlocking {
        val podcast = mockk<PodcastResponse>()
        val detailed = mockk<DetailedItem>()
        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Success(UserResponse(null))
        coEvery { repository.fetchPodcastItem("pod-1") } returns OperationResult.Success(podcast)
        every { podcastResponseConverter.apply(podcast, emptyList()) } returns detailed

        val result = channel.fetchBook("pod-1")

        assertEquals(detailed, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBook converts with empty progress when the user fetch fails`() =
      runBlocking {
        val podcast = mockk<PodcastResponse>()
        val detailed = mockk<DetailedItem>()
        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Error(OperationError.NetworkError)
        coEvery { repository.fetchPodcastItem("pod-1") } returns OperationResult.Success(podcast)
        every { podcastResponseConverter.apply(podcast, emptyList()) } returns detailed

        val result = channel.fetchBook("pod-1")

        assertEquals(detailed, (result as OperationResult.Success).data)
      }

    @Test
    fun `fetchBook propagates the podcast fetch error`() =
      runBlocking {
        coEvery { repository.fetchUserInfoResponse() } returns OperationResult.Success(UserResponse(null))
        coEvery { repository.fetchPodcastItem("pod-1") } returns OperationResult.Error(OperationError.NetworkError)

        val result = channel.fetchBook("pod-1")

        assertInstanceOf(OperationResult.Error::class.java, result)
        coVerify(exactly = 0) { podcastResponseConverter.apply(any(), any()) }
      }
  }

  @Test
  fun `library type is podcast`() {
    assertEquals(LibraryType.PODCAST, channel.getLibraryType())
  }

  private fun podcastItem(id: String): PodcastItem =
    PodcastItem(
      id = id,
      media =
        PodcastItemMedia(
          numEpisodes = 1,
          metadata = PodcastMetadata(title = "Podcast $id", author = "Author"),
        ),
    )

  private fun book(id: String): org.grakovne.lissen.domain.Book =
    org.grakovne.lissen.domain.Book(
      id = id,
      subtitle = null,
      series = null,
      title = "Podcast $id",
      author = "Author",
    )

  private fun mediaProgress(
    libraryItemId: String,
    episodeId: String?,
    lastUpdate: Long,
  ): MediaProgressResponse =
    MediaProgressResponse(
      libraryItemId = libraryItemId,
      episodeId = episodeId,
      currentTime = 0.0,
      isFinished = false,
      lastUpdate = lastUpdate,
      progress = 0.0,
    )

  private companion object {
    const val LIBRARY = "lib-1"
  }
}
