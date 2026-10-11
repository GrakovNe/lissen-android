package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryGroupingPreferencesTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val store = SecurePreferenceStore(context)
  private val preferences = LibraryPreferences(store)

  private val books = Library(id = "l1", title = "Books", type = LibraryType.LIBRARY)
  private val podcasts = Library(id = "l2", title = "Podcasts", type = LibraryType.PODCAST)

  @Test
  fun `a library without a choice is not grouped`() {
    assertEquals(LibraryGrouping.NONE, preferences.getLibraryGrouping("l1"))
    assertEquals(LibraryGrouping.NONE, preferences.getLibraryGrouping(null))
  }

  @Test
  fun `the grouping is kept per library`() {
    preferences.saveLibraryGrouping("l1", LibraryGrouping.SERIES)
    preferences.saveLibraryGrouping("l2", LibraryGrouping.PLAYLIST)

    assertEquals(LibraryGrouping.SERIES, preferences.getLibraryGrouping("l1"))
    assertEquals(LibraryGrouping.PLAYLIST, preferences.getLibraryGrouping("l2"))
    assertEquals(LibraryGrouping.NONE, preferences.getLibraryGrouping("l3"))
  }

  @Test
  fun `a choice made before groupings were kept per library applies until a library gets its own`() {
    store.putString("library_grouping", LibraryGrouping.AUTHOR.name)

    assertEquals(LibraryGrouping.AUTHOR, preferences.getLibraryGrouping("l1"))

    preferences.saveLibraryGrouping("l1", LibraryGrouping.NONE)

    assertEquals(LibraryGrouping.NONE, preferences.getLibraryGrouping("l1"))
    assertEquals(LibraryGrouping.AUTHOR, preferences.getLibraryGrouping("l2"))
  }

  @Test
  fun `flow follows the active library and its own choice`() =
    runTest {
      val emissions = mutableListOf<LibraryGrouping>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.libraryGroupingFlow.collect { emissions += it }
        }

      assertEquals(LibraryGrouping.NONE, emissions.last())

      preferences.savePreferredLibrary(books)
      preferences.saveLibraryGrouping(books.id, LibraryGrouping.SERIES)
      assertEquals(LibraryGrouping.SERIES, emissions.last())

      preferences.savePreferredLibrary(podcasts)
      assertEquals(LibraryGrouping.NONE, emissions.last())

      preferences.saveLibraryGrouping(podcasts.id, LibraryGrouping.PLAYLIST)
      assertEquals(LibraryGrouping.PLAYLIST, emissions.last())

      preferences.savePreferredLibrary(books)
      assertEquals(LibraryGrouping.SERIES, emissions.last())

      collector.cancel()
    }
}
