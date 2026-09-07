package org.grakovne.lissen.persistence.preferences

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferredLibraryTypeFlowTest {
  private val fakePreferences = FakeSharedPreferences()

  private val context =
    mockk<Context> {
      every { getSharedPreferences(any(), any()) } returns fakePreferences
    }

  private val preferences = LibraryPreferences(SecurePreferenceStore(context))

  @Test
  fun `flow starts with the current type`() =
    runTest {
      preferences.savePreferredLibrary(Library("lib-1", "Books", LibraryType.LIBRARY))

      val emissions = mutableListOf<LibraryType>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.preferredLibraryTypeFlow.collect { emissions += it }
        }

      assertEquals(listOf(LibraryType.LIBRARY), emissions)
      collector.cancel()
    }

  @Test
  fun `flow emits when the preferred library changes type`() =
    runTest {
      preferences.savePreferredLibrary(Library("lib-1", "Books", LibraryType.LIBRARY))

      val emissions = mutableListOf<LibraryType>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.preferredLibraryTypeFlow.collect { emissions += it }
        }

      preferences.savePreferredLibrary(Library("lib-2", "Podcasts", LibraryType.PODCAST))

      assertEquals(listOf(LibraryType.LIBRARY, LibraryType.PODCAST), emissions)
      collector.cancel()
    }

  @Test
  fun `switching between libraries of the same type does not re-emit`() =
    runTest {
      preferences.savePreferredLibrary(Library("lib-1", "Books", LibraryType.LIBRARY))

      val emissions = mutableListOf<LibraryType>()
      val collector =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          preferences.preferredLibraryTypeFlow.collect { emissions += it }
        }

      preferences.savePreferredLibrary(Library("lib-2", "More Books", LibraryType.LIBRARY))

      assertEquals(listOf(LibraryType.LIBRARY), emissions)
      collector.cancel()
    }
}
