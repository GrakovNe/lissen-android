package org.grakovne.lissen.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.MediaChannel
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedBookmarkProvider
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackSynchronizationService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class MediaLibrarySessionCallbackTest {
  private val context = mockk<Context>(relaxed = true)
  private val playbackPreferences = mockk<PlaybackPreferences>(relaxed = true)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val libraryTree = mockk<MediaLibraryTree>(relaxed = true)
  private val playbackSynchronizationService = mockk<PlaybackSynchronizationService>(relaxed = true)

  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val channelProvider = mockk<AudiobookshelfChannelProvider>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val cachedCoverProvider = mockk<CachedCoverProvider>(relaxed = true)
  private val cachedBookmarkProvider = mockk<CachedBookmarkProvider>(relaxed = true)
  private val mediaChannel = mockk<MediaChannel>(relaxed = true)

  private val mediaSession = mockk<MediaSession>(relaxed = true)
  private val controller = mockk<MediaSession.ControllerInfo>(relaxed = true)

  private val registeredBook = slot<DetailedItem>()

  private lateinit var callback: MediaLibrarySessionCallback

  @BeforeEach
  fun setup() {
    every { channelProvider.provideMediaChannel() } returns mediaChannel
    every { libraryPreferences.isForceCache() } returns false
    every { mediaRepository.registerPlayingBook(capture(registeredBook)) } returns Unit

    // The playback queue building touches android.os.Bundle and android.net.Uri,
    // which are stubbed in JVM unit tests.
    mockkConstructor(Bundle::class)
    every { anyConstructed<Bundle>().putLong(any(), any()) } returns Unit
    every { anyConstructed<Bundle>().putParcelableArrayList(any(), any()) } returns Unit
    mockkStatic(Uri::class)
    every { Uri.parse(any()) } returns mockk()

    val provider =
      LissenMediaProvider(
        libraryPreferences,
        channelProvider,
        localCacheRepository,
        cachedCoverProvider,
        cachedBookmarkProvider,
      )

    callback =
      MediaLibrarySessionCallback(
        context,
        playbackPreferences,
        mediaRepository,
        provider,
        libraryTree,
        playbackSynchronizationService,
      )
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Nested
  inner class OnPlaybackResumption {
    @Test
    fun `resumes at the dirty local progress when offline and the book was never downloaded`() {
      val storedBook = bookWithChapters(progress = MediaProgress(currentTime = 0.0, isFinished = false, lastUpdate = 100))
      val localProgress = MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 200, dirty = true)

      every { playbackPreferences.getPlayingItem() } returns storedBook
      coEvery { mediaChannel.fetchBook(storedBook.id) } returns
        OperationResult.Error(OperationError.NetworkError)
      coEvery { localCacheRepository.fetchBook(storedBook.id) } returns null
      coEvery { localCacheRepository.fetchPlayingItemProgress(storedBook.id) } returns localProgress

      val result = resumptionForPlayback()

      assertEquals(1, result.startIndex)
      assertEquals(150_000L, result.startPositionMs)
      assertEquals(localProgress, registeredBook.captured.progress)
      verify { playbackSynchronizationService.startPlaybackSynchronization(registeredBook.captured) }
    }

    @Test
    fun `resumes at the dirty local progress when offline and the cached copy is stale`() {
      val storedBook = bookWithChapters(progress = MediaProgress(currentTime = 0.0, isFinished = false, lastUpdate = 100))
      val cachedBook = bookWithChapters(progress = MediaProgress(currentTime = 10.0, isFinished = false, lastUpdate = 100))
      val localProgress = MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 200, dirty = true)

      every { playbackPreferences.getPlayingItem() } returns storedBook
      coEvery { mediaChannel.fetchBook(storedBook.id) } returns
        OperationResult.Error(OperationError.NetworkError)
      coEvery { localCacheRepository.fetchBook(storedBook.id) } returns cachedBook
      coEvery { localCacheRepository.fetchPlayingItemProgress(storedBook.id) } returns localProgress

      val result = resumptionForPlayback()

      assertEquals(1, result.startIndex)
      assertEquals(150_000L, result.startPositionMs)
      assertEquals(localProgress, registeredBook.captured.progress)
    }

    @Test
    fun `resumes at the dirty local progress even when the network refresh succeeds`() {
      val storedBook = bookWithChapters(progress = MediaProgress(currentTime = 0.0, isFinished = false, lastUpdate = 100))
      val refreshedBook = bookWithChapters(progress = MediaProgress(currentTime = 50.0, isFinished = false, lastUpdate = 999))
      val localProgress = MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 200, dirty = true)

      every { playbackPreferences.getPlayingItem() } returns storedBook
      coEvery { mediaChannel.fetchBook(storedBook.id) } returns OperationResult.Success(refreshedBook)
      coEvery { localCacheRepository.fetchPlayingItemProgress(storedBook.id) } returns localProgress

      val result = resumptionForPlayback()

      assertEquals(1, result.startIndex)
      assertEquals(150_000L, result.startPositionMs)
      assertEquals(localProgress, registeredBook.captured.progress)
    }

    @Test
    fun `resumes at the first available chapter when the dirty progress points at an uncached chapter`() {
      // Partially downloaded book: chapter 1 is cached, chapter 2 is not.
      val storedBook =
        bookWithChapters(
          progress = MediaProgress(currentTime = 0.0, isFinished = false, lastUpdate = 100),
          chapterAvailability = listOf(true, false),
        )
      val localProgress = MediaProgress(currentTime = 450.0, isFinished = false, lastUpdate = 200, dirty = true)

      every { playbackPreferences.getPlayingItem() } returns storedBook
      coEvery { mediaChannel.fetchBook(storedBook.id) } returns
        OperationResult.Error(OperationError.NetworkError)
      coEvery { localCacheRepository.fetchBook(storedBook.id) } returns storedBook
      coEvery { localCacheRepository.fetchPlayingItemProgress(storedBook.id) } returns localProgress

      val result = resumptionForPlayback()

      // The overlaid position (chapter 2, 450s) is not playable offline, so
      // resumption must land on the first available chapter (chapter 1, 0s).
      assertEquals(0, result.startIndex)
      assertEquals(0L, result.startPositionMs)
      assertEquals(0.0, registeredBook.captured.progress?.currentTime)
      verify { playbackSynchronizationService.startPlaybackSynchronization(registeredBook.captured) }
    }
  }

  private fun resumptionForPlayback(): MediaItemsWithStartPosition =
    callback
      .onPlaybackResumption(mediaSession, controller, isForPlayback = true)
      .get(5, TimeUnit.SECONDS)

  private fun bookWithChapters(
    progress: MediaProgress,
    chapterAvailability: List<Boolean> = listOf(true, true),
  ): DetailedItem =
    DetailedItem(
      id = "book-1",
      title = "Test Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files =
        listOf(
          BookFile(id = "f1", name = "part-1.mp3", duration = 300.0, size = null, mimeType = "audio/mpeg"),
          BookFile(id = "f2", name = "part-2.mp3", duration = 300.0, size = null, mimeType = "audio/mpeg"),
        ),
      chapters =
        listOf(
          PlayingChapter(
            available = chapterAvailability[0],
            podcastEpisodeState = null,
            duration = 300.0,
            start = 0.0,
            end = 300.0,
            title = "Chapter 1",
            id = "c1",
          ),
          PlayingChapter(
            available = chapterAvailability[1],
            podcastEpisodeState = null,
            duration = 300.0,
            start = 300.0,
            end = 600.0,
            title = "Chapter 2",
            id = "c2",
          ),
        ),
      progress = progress,
      libraryId = "lib-1",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
