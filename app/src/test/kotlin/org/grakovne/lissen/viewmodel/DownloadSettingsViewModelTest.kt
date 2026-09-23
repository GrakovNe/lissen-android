package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.cache.persistent.ContentCachingManager
import org.grakovne.lissen.content.cache.persistent.OfflineBookStorageProperties
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadSettingsViewModelTest {
  private val download = mockk<DownloadPreferences>(relaxed = true)
  private val offlineBookStorageProperties = mockk<OfflineBookStorageProperties>(relaxed = true)
  private val contentCachingManager = mockk<ContentCachingManager>(relaxed = true)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private lateinit var viewModel: DownloadSettingsViewModel

  private fun buildViewModel() = DownloadSettingsViewModel(download, offlineBookStorageProperties, contentCachingManager, mediaRepository)

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    every { download.getAutoDownloadNetworkType() } returns NetworkTypeAutoCache.WIFI_ONLY
    every { download.getAutoDownloadLibraryTypes() } returns LibraryType.entries
    every { download.getAutoDownloadOption() } returns null
    every { download.getAutoDownloadDelayed() } returns false
    every { mediaRepository.playingBook } returns MutableStateFlow(null)

    viewModel = buildViewModel()
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class DownloadStorage {
    private val internal =
      StoragePath(
        path = "/storage/emulated/0/Android/data/org.grakovne.lissen/files/media_cache",
        name = "Internal storage",
      )
    private val card =
      StoragePath(
        path = "/storage/0000-0000/Android/data/org.grakovne.lissen/files/media_cache",
        name = "SD card",
      )

    @Test
    fun `preferDownloadStorage drops cache before saving the new path`() =
      runTest {
        every { offlineBookStorageProperties.provideAvailableStorages() } returns listOf(internal, card)
        every { offlineBookStorageProperties.provideActiveStorage() } returns File(internal.path)
        val viewModel = buildViewModel()

        val result = viewModel.preferDownloadStorage(card)

        assertTrue(result)
        coVerifyOrder {
          contentCachingManager.dropAllCache()
          download.saveDownloadStoragePath(card)
        }
        assertEquals(card, viewModel.downloadStorage.value)
        assertEquals(card, viewModel.downloadStoragePath.value)
        assertFalse(viewModel.downloadStorageClearing.value)
      }

    @Test
    fun `preferDownloadStorage saves the path without dropping when folder is already active`() =
      runTest {
        every { offlineBookStorageProperties.provideAvailableStorages() } returns listOf(internal)
        every { offlineBookStorageProperties.provideActiveStorage() } returns File(internal.path)
        val viewModel = buildViewModel()

        val result = viewModel.preferDownloadStorage(internal)

        assertTrue(result)
        coVerify(exactly = 0) { contentCachingManager.dropAllCache() }
        verify { download.saveDownloadStoragePath(internal) }
      }

    @Test
    fun `preferDownloadStorage rejects folder which is not available`() =
      runTest {
        every { offlineBookStorageProperties.provideAvailableStorages() } returns listOf(internal)
        every { offlineBookStorageProperties.provideActiveStorage() } returns File(internal.path)
        val viewModel = buildViewModel()

        val result = viewModel.preferDownloadStorage(card)

        assertFalse(result)
        coVerify(exactly = 0) { contentCachingManager.dropAllCache() }
        verify(exactly = 0) { download.saveDownloadStoragePath(any()) }
      }

    @Test
    fun `preferDownloadStorage clears the playing book when it is cached`() =
      runTest {
        every { offlineBookStorageProperties.provideAvailableStorages() } returns listOf(internal, card)
        every { offlineBookStorageProperties.provideActiveStorage() } returns File(internal.path)
        val playingBook = mockk<DetailedItem> { every { id } returns "book-1" }
        every { mediaRepository.playingBook } returns MutableStateFlow(playingBook)
        every { contentCachingManager.hasMetadataCached("book-1") } returns flowOf(true)
        val viewModel = buildViewModel()

        viewModel.preferDownloadStorage(card)

        coVerifyOrder {
          mediaRepository.clearPlayingBook()
          contentCachingManager.dropAllCache()
        }
      }

    @Test
    fun `preferDownloadStorage keeps the old path when dropping fails`() =
      runTest {
        every { offlineBookStorageProperties.provideAvailableStorages() } returns listOf(internal, card)
        every { offlineBookStorageProperties.provideActiveStorage() } returns File(internal.path)
        coEvery { contentCachingManager.dropAllCache() } throws IllegalStateException("db error")
        val viewModel = buildViewModel()

        val result = viewModel.preferDownloadStorage(card)

        assertFalse(result)
        verify(exactly = 0) { download.saveDownloadStoragePath(any()) }
        assertFalse(viewModel.downloadStorageClearing.value)
      }
  }

  @Nested
  inner class AutoDownloadNetworkType {
    @Test
    fun `preferAutoDownloadNetworkType updates StateFlow`() {
      viewModel.preferAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_OR_CELLULAR)
      assertEquals(NetworkTypeAutoCache.WIFI_OR_CELLULAR, viewModel.preferredAutoDownloadNetworkType.value)
    }

    @Test
    fun `preferAutoDownloadNetworkType saves to preferences`() {
      viewModel.preferAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_ONLY)
      verify { download.saveAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_ONLY) }
    }
  }

  @Nested
  inner class AutoDownloadLibraryType {
    @Test
    fun `changeAutoDownloadLibraryType adds type when state is true`() {
      every { download.getAutoDownloadLibraryTypes() } returns listOf(LibraryType.LIBRARY)
      viewModel = buildViewModel()

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, true)

      assertEquals(listOf(LibraryType.LIBRARY, LibraryType.PODCAST), viewModel.preferredAutoDownloadLibraryTypes.value)
    }

    @Test
    fun `changeAutoDownloadLibraryType removes type when state is false`() {
      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, false)

      assertFalse(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType saves updated list to preferences`() {
      viewModel.changeAutoDownloadLibraryType(LibraryType.LIBRARY, false)
      verify { download.saveAutoDownloadLibraryTypes(LibraryType.entries - LibraryType.LIBRARY) }
    }
  }

  @Nested
  inner class Toggles {
    @Test
    fun `preferAutoDownloadDelayed updates StateFlow and preferences`() {
      viewModel.preferAutoDownloadDelayed(true)

      assertTrue(viewModel.autoDownloadDelayed.value)
      verify { download.saveAutoDownloadDelayed(true) }
    }

    @Test
    fun `preferAutoDownloadOption updates StateFlow and preferences`() {
      viewModel.preferAutoDownloadOption(CurrentItemDownloadOption)

      assertEquals(CurrentItemDownloadOption, viewModel.preferredAutoDownloadOption.value)
      verify { download.saveAutoDownloadOption(CurrentItemDownloadOption) }
    }
  }
}
