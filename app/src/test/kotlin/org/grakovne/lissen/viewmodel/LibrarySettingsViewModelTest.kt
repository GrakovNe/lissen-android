package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.LibraryGrouping
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibrarySettingsViewModelTest {
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private lateinit var viewModel: LibrarySettingsViewModel

  private val books = Library(id = "l1", title = "Books", type = LibraryType.LIBRARY)
  private val podcasts = Library(id = "l2", title = "Podcasts", type = LibraryType.PODCAST)

  private fun buildViewModel() = LibrarySettingsViewModel(mediaChannel, libraryPreferences)

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    every { libraryPreferences.getPreferredLibrary() } returns null
    every { libraryPreferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    every { libraryPreferences.hideCompletedFlow } returns flowOf(false)

    viewModel = buildViewModel()
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class LibraryPreference {
    @Test
    fun `preferLibrary updates preferredLibrary StateFlow`() {
      viewModel.preferLibrary(books)
      assertEquals(books, viewModel.preferredLibrary.value)
    }

    @Test
    fun `preferLibrary saves library to preferences`() {
      viewModel.preferLibrary(podcasts)
      verify { libraryPreferences.savePreferredLibrary(podcasts) }
    }

    @Test
    fun `fetchPreferredLibraryId returns the preferred library id`() {
      every { libraryPreferences.getPreferredLibrary() } returns books

      assertEquals("l1", viewModel.fetchPreferredLibraryId())
    }

    @Test
    fun `fetchPreferredLibraryId returns empty string when no preferred library`() {
      assertEquals("", viewModel.fetchPreferredLibraryId())
    }
  }

  @Nested
  inner class FetchLibraries {
    @Test
    fun `fetchLibraries populates libraries on success`() {
      coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(listOf(books, podcasts))

      viewModel.fetchLibraries()

      assertEquals(listOf(books, podcasts), viewModel.libraries.value)
    }

    @Test
    fun `fetchLibraries selects matching preferred library`() {
      every { libraryPreferences.getPreferredLibrary() } returns podcasts
      coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(listOf(books, podcasts))
      viewModel = buildViewModel()

      viewModel.fetchLibraries()

      assertEquals(podcasts, viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries selects first library when no preferred set`() {
      coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(listOf(books))

      viewModel.fetchLibraries()

      assertEquals(books, viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries falls back to cached preferred library on error`() {
      every { libraryPreferences.getPreferredLibrary() } returns books
      coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Error(OperationError.NetworkError)
      viewModel = buildViewModel()

      viewModel.fetchLibraries()

      assertEquals(listOf(books), viewModel.libraries.value)
    }
  }

  @Nested
  inner class LibraryOrdering {
    private val byAuthor = LibraryOrderingConfiguration(LibraryOrderingOption.AUTHOR, LibraryOrderingDirection.DESCENDING)

    @Test
    fun `preferLibraryOrdering updates StateFlow`() {
      viewModel.preferLibraryOrdering(byAuthor)
      assertEquals(byAuthor, viewModel.preferredLibraryOrdering.value)
    }

    @Test
    fun `preferLibraryOrdering saves to preferences`() {
      viewModel.preferLibraryOrdering(LibraryOrderingConfiguration.default)
      verify { libraryPreferences.saveLibraryOrdering(LibraryOrderingConfiguration.default) }
    }

    @Test
    fun `fetchLibraryOrdering delegates to preferences`() {
      every { libraryPreferences.getLibraryOrdering() } returns byAuthor

      assertEquals(byAuthor, viewModel.fetchLibraryOrdering())
    }
  }

  @Nested
  inner class HideCompletedToggle {
    @Test
    fun `toggleHideCompleted saves true when currently false`() {
      every { libraryPreferences.getHideCompleted() } returns false

      viewModel.toggleHideCompleted()

      verify { libraryPreferences.saveHideCompleted(true) }
    }

    @Test
    fun `toggleHideCompleted saves false when currently true`() {
      every { libraryPreferences.getHideCompleted() } returns true

      viewModel.toggleHideCompleted()

      verify { libraryPreferences.saveHideCompleted(false) }
    }
  }

  @Test
  fun `preferLibraryGrouping saves the grouping to preferences`() {
    viewModel.preferLibraryGrouping(LibraryGrouping.SERIES)

    verify { libraryPreferences.saveLibraryGrouping(LibraryGrouping.SERIES) }
  }
}
