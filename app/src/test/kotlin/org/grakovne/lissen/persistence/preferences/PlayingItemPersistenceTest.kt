package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class PlayingItemPersistenceTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val store = SecurePreferenceStore(context)
  private val library = LibraryPreferences(store)
  private val preferences = PlaybackPreferences(store, library)

  private fun item(
    id: String,
    libraryId: String,
  ) = DetailedItem(
    id = id,
    title = "Title $id",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = null,
    libraryId = libraryId,
    localProvided = false,
    createdAt = 0L,
    updatedAt = 0L,
  )

  private fun preferLibrary(libraryId: String) = library.savePreferredLibrary(Library(libraryId, "Library", LibraryType.LIBRARY))

  @Test
  fun `stored playing items are parsed once and then served from memory`() {
    preferLibrary("lib-1")
    preferences.savePlayingItem(item(id = "book-1", libraryId = "lib-1"))

    repeat(10) {
      assertEquals("book-1", preferences.getPlayingItem()?.id)
    }

    assertEquals(1, fakePreferences.readsOf("playing_item"))
  }

  @Test
  fun `playing item round-trips per library`() {
    preferLibrary("lib-1")
    preferences.savePlayingItem(item(id = "book-1", libraryId = "lib-1"))

    assertEquals("book-1", preferences.getPlayingItem()?.id)

    preferences.clearPlayingItem()
    assertNull(preferences.getPlayingItem())
  }

  @Test
  fun `concurrent saves from different libraries do not lose each other`() {
    val items = (1..8).map { item(id = "book-$it", libraryId = "lib-$it") }
    val startGate = CountDownLatch(1)

    val threads =
      items.map { detailedItem ->
        thread {
          startGate.await()
          preferences.savePlayingItem(detailedItem)
        }
      }

    startGate.countDown()
    threads.forEach { it.join() }

    items.forEach { detailedItem ->
      preferLibrary(detailedItem.libraryId!!)
      assertEquals(detailedItem.id, preferences.getPlayingItem()?.id)
    }
  }

  @Test
  fun `concurrent save and clear keep unrelated libraries intact`() {
    preferLibrary("lib-keep")
    preferences.savePlayingItem(item(id = "book-keep", libraryId = "lib-keep"))

    val startGate = CountDownLatch(1)
    val saver =
      thread {
        startGate.await()
        preferences.savePlayingItem(item(id = "book-new", libraryId = "lib-new"))
      }
    val cleaner =
      thread {
        startGate.await()
        preferLibrary("lib-other")
        preferences.clearPlayingItem()
      }

    startGate.countDown()
    saver.join()
    cleaner.join()

    preferLibrary("lib-keep")
    assertEquals("book-keep", preferences.getPlayingItem()?.id)

    preferLibrary("lib-new")
    assertEquals("book-new", preferences.getPlayingItem()?.id)
  }
}
