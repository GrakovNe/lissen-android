package org.grakovne.lissen.viewmodel

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.ContentCachingProgress
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.content.cache.temporary.CachedCoverProvider
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import org.grakovne.lissen.domain.CacheStatus
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachingModelViewTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val context = mockk<Context>(relaxed = true)
  private val localCacheRepository = mockk<LocalCacheRepository>(relaxed = true)
  private val contentCachingProgress = mockk<ContentCachingProgress>(relaxed = true)
  private val contentCachingManager = mockk<ContentCachingManager>(relaxed = true)
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val cachedCoverProvider = mockk<CachedCoverProvider>(relaxed = true)
  private val seriesCoverProvider = mockk<SeriesCoverProvider>(relaxed = true)

  private val statusFlow = MutableSharedFlow<Pair<DetailedItem, CacheState>>(replay = 1)

  private lateinit var viewModel: CachingModelView

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    every { contentCachingProgress.statusFlow } returns statusFlow
    every { preferences.forceCacheFlow } returns flowOf(false)

    viewModel =
      CachingModelView(
        context,
        localCacheRepository,
        contentCachingProgress,
        contentCachingManager,
        preferences,
        cachedCoverProvider,
        seriesCoverProvider,
      )
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class TotalCount {
    @Test
    fun `totalCount is initially 0`() {
      assertEquals(0, viewModel.totalCount.value)
    }
  }

  @Nested
  inner class CacheProgress {
    @Test
    fun `getProgress returns Idle for unknown book`() {
      val progress = viewModel.getProgress("unknown-book")
      assertEquals(CacheStatus.Idle, progress.value.status)
    }

    @Test
    fun `getProgress returns same flow for same book id`() {
      val first = viewModel.getProgress("book-1")
      val second = viewModel.getProgress("book-1")
      assertTrue(first === second)
    }

    @Test
    fun `progress updates from contentCachingProgress`() =
      runTest {
        val item = detailedItem(id = "book-1")
        val state = CacheState(CacheStatus.Caching, 0.5)

        statusFlow.emit(item to state)

        val progress = viewModel.getProgress("book-1")
        assertEquals(CacheStatus.Caching, progress.value.status)
        assertEquals(0.5, progress.value.progress)
      }
  }

  @Nested
  inner class ProvideCacheState {
    @Test
    fun `provideCacheState delegates to contentCachingManager`() {
      every { contentCachingManager.hasMetadataCached("book-1") } returns flowOf(true)

      val flow = viewModel.provideCacheState("book-1")

      verify { contentCachingManager.hasMetadataCached("book-1") }
    }

    @Test
    fun `provideCacheState with chapter delegates to contentCachingManager`() {
      every { contentCachingManager.hasMetadataCached("book-1", "ch-1") } returns flowOf(false)

      val flow = viewModel.provideCacheState("book-1", "ch-1")

      verify { contentCachingManager.hasMetadataCached("book-1", "ch-1") }
    }
  }

  @Nested
  inner class CacheForce {
    @Test
    fun `toggleCacheForce enables when currently disabled`() {
      every { preferences.isForceCache() } returns false

      viewModel.toggleCacheForce()

      verify { preferences.enableForceCache() }
    }

    @Test
    fun `toggleCacheForce disables when currently enabled`() {
      every { preferences.isForceCache() } returns true

      viewModel.toggleCacheForce()

      verify { preferences.disableForceCache() }
    }

    @Test
    fun `localCacheUsing delegates to preferences`() {
      every { preferences.isForceCache() } returns true
      assertTrue(viewModel.localCacheUsing())

      every { preferences.isForceCache() } returns false
      assertFalse(viewModel.localCacheUsing())
    }
  }

  @Nested
  inner class DropCache {
    @Test
    fun `dropCache by id delegates to contentCachingManager`() =
      runTest {
        viewModel.dropCache("book-1")
        coVerify { contentCachingManager.dropCache("book-1") }
      }

    @Test
    fun `dropCache by item and chapter delegates to contentCachingManager`() =
      runTest {
        val item = detailedItem(id = "book-1")
        val chapter = playingChapter(id = "ch-1")

        viewModel.dropCache(item, chapter)

        coVerify { contentCachingManager.dropCache(item, chapter) }
      }
  }

  @Nested
  inner class ClearCache {
    @Test
    fun `clearShortTermCache delegates to cachedCoverProvider`() =
      runTest {
        viewModel.clearShortTermCache()
        coVerify { cachedCoverProvider.clearCache() }
      }
  }

  @Nested
  inner class FetchLatestUpdate {
    @Test
    fun `fetchLatestUpdate delegates to localCacheRepository`() =
      runTest {
        coEvery { localCacheRepository.fetchLatestUpdate("lib-1") } returns 12345L

        val result = viewModel.fetchLatestUpdate("lib-1")

        assertEquals(12345L, result)
      }
  }

  private fun detailedItem(id: String = "book-1") =
    DetailedItem(
      id = id,
      title = "Test Book",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters = emptyList(),
      progress = null,
      libraryId = "lib-1",
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )

  private fun playingChapter(id: String = "ch-1") =
    org.grakovne.lissen.domain.PlayingChapter(
      id = id,
      title = "Chapter 1",
      start = 0.0,
      end = 100.0,
      duration = 100.0,
      available = true,
      podcastEpisodeState = org.grakovne.lissen.domain.BookChapterState.FINISHED,
    )
}
