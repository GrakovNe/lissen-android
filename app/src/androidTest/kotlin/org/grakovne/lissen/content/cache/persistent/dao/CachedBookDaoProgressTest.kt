package org.grakovne.lissen.content.cache.persistent.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.cache.persistent.LocalCacheStorage
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CachedBookDaoProgressTest {
  private lateinit var db: LocalCacheStorage
  private lateinit var dao: CachedBookDao

  @Before
  fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db =
      Room
        .inMemoryDatabaseBuilder(context, LocalCacheStorage::class.java)
        .allowMainThreadQueries()
        .build()
    dao = db.cachedBookDao()
  }

  @After
  fun teardown() = db.close()

  private fun book(
    id: String,
    progress: MediaProgress?,
  ): DetailedItem =
    DetailedItem(
      id = id,
      title = "Book $id",
      subtitle = null,
      author = "Author",
      narrator = null,
      publisher = null,
      series = emptyList(),
      year = null,
      abstract = null,
      files = emptyList(),
      progress = progress,
      libraryId = LIBRARY,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
      chapters =
        listOf(
          PlayingChapter(
            id = "chapter-1",
            title = "Chapter 1",
            start = 0.0,
            end = 100.0,
            duration = 100.0,
            available = true,
            podcastEpisodeState = null,
          ),
        ),
    )

  @Test
  fun dirtyProgressSurvivesCachingRunCarryingOlderProgress() =
    runBlocking {
      val id = "book-1"
      dao.upsertCachedBook(
        book(id, MediaProgress(currentTime = 100.0, isFinished = false, lastUpdate = 2_000, dirty = true)),
        emptyList(),
        emptyList(),
      )

      dao.upsertCachedBook(
        book(id, MediaProgress(currentTime = 10.0, isFinished = false, lastUpdate = 1_000, dirty = false)),
        emptyList(),
        emptyList(),
      )

      val progress = dao.fetchMediaProgress(id)
      assertNotNull(progress)
      assertEquals(100.0, progress!!.currentTime, 0.0)
      assertEquals(2_000, progress.lastUpdate)
      assertTrue(progress.dirty)

      // The rest of the upsert is unaffected.
      assertNotNull(dao.fetchCachedBook(id))
    }

  @Test
  fun nonDirtyProgressIsReplacedByCachingRun() =
    runBlocking {
      val id = "book-2"
      dao.upsertCachedBook(
        book(id, MediaProgress(currentTime = 50.0, isFinished = false, lastUpdate = 1_500, dirty = false)),
        emptyList(),
        emptyList(),
      )

      dao.upsertCachedBook(
        book(id, MediaProgress(currentTime = 10.0, isFinished = false, lastUpdate = 1_000, dirty = false)),
        emptyList(),
        emptyList(),
      )

      val progress = dao.fetchMediaProgress(id)
      assertNotNull(progress)
      assertEquals(10.0, progress!!.currentTime, 0.0)
      assertEquals(1_000, progress.lastUpdate)
    }

  companion object {
    private const val LIBRARY = "lib-1"
  }
}
