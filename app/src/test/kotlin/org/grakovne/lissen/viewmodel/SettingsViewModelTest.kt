package org.grakovne.lissen.viewmodel

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
  private val testDispatcher = UnconfinedTestDispatcher()
  private val preferences = mockk<LissenSharedPreferences>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val logProvider = mockk<LissenLogProvider>(relaxed = true)
  private lateinit var viewModel: SettingsViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)

    every { preferences.getHost() } returns "http://example.com"
    every { preferences.getUsername() } returns "user"
    every { preferences.getServerVersion() } returns "1.0.0"
    every { preferences.getPreferredLibrary() } returns null
    every { preferences.getColorScheme() } returns ColorScheme.FOLLOW_SYSTEM
    every { preferences.getMaterialYouColors() } returns false
    every { preferences.getAutoDownloadNetworkType() } returns NetworkTypeAutoCache.WIFI_ONLY
    every { preferences.getAutoDownloadLibraryTypes() } returns LibraryType.meaningfulTypes
    every { preferences.getAutoDownloadOption() } returns null
    every { preferences.getPlaybackVolumeBoost() } returns 0
    every { preferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    every { preferences.getCustomHeaders() } returns emptyList()
    every { preferences.getLocalUrls() } returns emptyList()
    every { preferences.getSeekTime() } returns SeekTime.Default
    every { preferences.getAcraEnabled() } returns true
    every { preferences.getSslBypass() } returns false
    every { preferences.getSoftwareCodecsEnabled() } returns false
    every { preferences.isActivityLoggingEnabled() } returns true
    every { preferences.getAutoDownloadDelayed() } returns false
    every { preferences.getUserAgent() } returns DEFAULT_USER_AGENT
    every { preferences.clientCertAliasFlow } returns flowOf(null)
    every { preferences.hideCompletedFlow } returns flowOf(false)
    every { mediaChannel.fetchConnectionHost() } returns
      OperationResult.Error(
        org.grakovne.lissen.channel.common.OperationError.NetworkError,
      )

    viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class LibraryPreference {
    @Test
    fun `preferLibrary updates preferredLibrary StateFlow`() {
      val library = Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)
      viewModel.preferLibrary(library)
      assertEquals(library, viewModel.preferredLibrary.value)
    }

    @Test
    fun `preferLibrary saves library to preferences`() {
      val library = Library(id = "lib-2", title = "Podcasts", type = LibraryType.PODCAST)
      viewModel.preferLibrary(library)
      verify { preferences.savePreferredLibrary(library) }
    }
  }

  @Nested
  inner class ColorSchemePreference {
    @Test
    fun `preferColorScheme updates StateFlow`() {
      viewModel.preferColorScheme(ColorScheme.DARK)
      assertEquals(ColorScheme.DARK, viewModel.preferredColorScheme.value)
    }

    @Test
    fun `preferColorScheme saves to preferences`() {
      viewModel.preferColorScheme(ColorScheme.LIGHT)
      verify { preferences.saveColorScheme(ColorScheme.LIGHT) }
    }
  }

  @Nested
  inner class MaterialYouPreference {
    @Test
    fun `preferMaterialYouColors updates StateFlow`() {
      viewModel.preferMaterialYouColors(true)
      assertTrue(viewModel.materialYouEnabled.value == true)
    }

    @Test
    fun `preferMaterialYouColors saves to preferences`() {
      viewModel.preferMaterialYouColors(false)
      verify { preferences.saveMaterialYouColors(false) }
    }
  }

  @Nested
  inner class AutoDownloadNetworkType {
    @Test
    fun `preferAutoDownloadNetworkType updates StateFlow`() {
      viewModel.preferAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_OR_CELLULAR)
      assertEquals(
        NetworkTypeAutoCache.WIFI_OR_CELLULAR,
        viewModel.preferredAutoDownloadNetworkType.value,
      )
    }

    @Test
    fun `preferAutoDownloadNetworkType saves to preferences`() {
      viewModel.preferAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_ONLY)
      verify { preferences.saveAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_ONLY) }
    }
  }

  @Nested
  inner class AutoDownloadLibraryType {
    @Test
    fun `changeAutoDownloadLibraryType adds type when state is true`() {
      every { preferences.getAutoDownloadLibraryTypes() } returns listOf(LibraryType.LIBRARY)
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, true)

      assertTrue(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType removes type when state is false`() {
      every { preferences.getAutoDownloadLibraryTypes() } returns LibraryType.meaningfulTypes
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, false)

      assertFalse(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType saves updated list to preferences`() {
      viewModel.changeAutoDownloadLibraryType(LibraryType.LIBRARY, false)
      verify { preferences.saveAutoDownloadLibraryTypes(any()) }
    }
  }

  @Nested
  inner class LibraryOrdering {
    @Test
    fun `preferLibraryOrdering updates StateFlow`() {
      val config =
        LibraryOrderingConfiguration(
          option = LibraryOrderingOption.AUTHOR,
          direction = LibraryOrderingDirection.DESCENDING,
        )
      viewModel.preferLibraryOrdering(config)
      assertEquals(config, viewModel.preferredLibraryOrdering.value)
    }

    @Test
    fun `preferLibraryOrdering saves to preferences`() {
      val config = LibraryOrderingConfiguration.default
      viewModel.preferLibraryOrdering(config)
      verify { preferences.saveLibraryOrdering(config) }
    }
  }

  @Nested
  inner class VolumeBoost {
    @Test
    fun `preferPlaybackVolumeBoost updates StateFlow`() {
      viewModel.preferPlaybackVolumeBoost(12)
      assertEquals(12, viewModel.preferredPlaybackVolumeBoost.value)
    }

    @Test
    fun `preferPlaybackVolumeBoost saves to preferences`() {
      viewModel.preferPlaybackVolumeBoost(6)
      verify { preferences.savePlaybackVolumeBoost(6) }
    }
  }

  @Nested
  inner class CrashReporting {
    @Test
    fun `preferCrashReporting updates StateFlow`() {
      viewModel.preferCrashReporting(false)
      assertFalse(viewModel.crashReporting.value == true)
    }

    @Test
    fun `preferCrashReporting saves to preferences`() {
      viewModel.preferCrashReporting(true)
      verify { preferences.saveAcraEnabled(true) }
    }
  }

  @Nested
  inner class SslBypass {
    @Test
    fun `preferBypassSsl updates StateFlow`() {
      viewModel.preferBypassSsl(true)
      assertTrue(viewModel.bypassSsl.value == true)
    }

    @Test
    fun `preferBypassSsl saves to preferences`() {
      viewModel.preferBypassSsl(false)
      verify { preferences.saveSslBypass(false) }
    }
  }

  @Nested
  inner class SeekTimePreference {
    @Test
    fun `preferForwardRewind updates seek forward`() {
      viewModel.preferForwardRewind(60)
      assertEquals(60, viewModel.seekTime.value.forward)
    }

    @Test
    fun `preferRewindRewind updates seek rewind`() {
      viewModel.preferRewindRewind(30)
      assertEquals(30, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferForwardRewind preserves rewind value`() {
      viewModel.preferForwardRewind(60)
      assertEquals(SeekTime.Default.rewind, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferRewindRewind preserves forward value`() {
      viewModel.preferRewindRewind(10)
      assertEquals(SeekTime.Default.forward, viewModel.seekTime.value.forward)
    }
  }

  @Nested
  inner class LocalUrls {
    @Test
    fun `updateLocalUrls filters out entries with empty ssid`() {
      val urls =
        listOf(
          LocalUrl(ssid = "", route = "http://192.168.1.1"),
          LocalUrl(ssid = "MyWifi", route = "http://192.168.1.2"),
        )
      viewModel.updateLocalUrls(urls)
      verify {
        preferences.saveLocalUrls(
          match { saved -> saved.none { it.ssid.isEmpty() } },
        )
      }
    }

    @Test
    fun `updateLocalUrls keeps entries with valid ssid and route`() {
      val urls =
        listOf(
          LocalUrl(ssid = "HomeWifi", route = "http://192.168.1.1"),
          LocalUrl(ssid = "WorkWifi", route = "http://10.0.0.1"),
        )
      viewModel.updateLocalUrls(urls)
      verify {
        preferences.saveLocalUrls(match { it.size == 2 })
      }
    }

    @Test
    fun `updateLocalUrls deduplicates by ssid`() {
      val urls =
        listOf(
          LocalUrl(ssid = "WiFi", route = "http://192.168.1.1"),
          LocalUrl(ssid = "WiFi", route = "http://192.168.1.2"),
        )
      viewModel.updateLocalUrls(urls)
      verify {
        preferences.saveLocalUrls(match { it.size == 1 })
      }
    }
  }

  @Nested
  inner class CustomHeaders {
    @Test
    fun `updateCustomHeaders filters out entries with empty name`() {
      val headers =
        listOf(
          ServerRequestHeader(name = "", value = "value1"),
          ServerRequestHeader(name = "X-Custom", value = "value2"),
        )
      viewModel.updateCustomHeaders(headers)
      verify {
        preferences.saveCustomHeaders(
          match { saved -> saved.none { it.name.isEmpty() } },
        )
      }
    }

    @Test
    fun `updateCustomHeaders filters out entries with empty value`() {
      val headers =
        listOf(
          ServerRequestHeader(name = "X-Token", value = ""),
          ServerRequestHeader(name = "X-Key", value = "abc"),
        )
      viewModel.updateCustomHeaders(headers)
      verify {
        preferences.saveCustomHeaders(
          match { saved -> saved.none { it.value.isEmpty() } },
        )
      }
    }

    @Test
    fun `updateCustomHeaders deduplicates by name`() {
      val headers =
        listOf(
          ServerRequestHeader(name = "X-Token", value = "first"),
          ServerRequestHeader(name = "X-Token", value = "second"),
        )
      viewModel.updateCustomHeaders(headers)
      verify {
        preferences.saveCustomHeaders(match { it.size == 1 })
      }
    }
  }

  @Nested
  inner class UserAgentPreference {
    @Test
    fun `updateUserAgent updates StateFlow`() {
      viewModel.updateUserAgent("CustomAgent/1.0")
      assertEquals("CustomAgent/1.0", viewModel.userAgent.value)
    }

    @Test
    fun `updateUserAgent saves to preferences`() {
      viewModel.updateUserAgent("CustomAgent/1.0")
      verify { preferences.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `resetUserAgent calls clearUserAgent on preferences`() {
      viewModel.resetUserAgent()
      verify { preferences.clearUserAgent() }
    }

    @Test
    fun `resetUserAgent restores StateFlow to DEFAULT_USER_AGENT`() {
      viewModel.updateUserAgent("CustomAgent/1.0")
      viewModel.resetUserAgent()
      assertEquals(DEFAULT_USER_AGENT, viewModel.userAgent.value)
    }

    @Test
    fun `userAgent StateFlow is initialized from preferences`() {
      every { preferences.getUserAgent() } returns "StoredAgent/3.0"
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)
      assertEquals("StoredAgent/3.0", viewModel.userAgent.value)
    }

    @Test
    fun `updateUserAgent strips newline characters`() {
      viewModel.updateUserAgent("Custom\nAgent/1.0")
      verify { preferences.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `updateUserAgent strips carriage return characters`() {
      viewModel.updateUserAgent("Custom\rAgent/1.0")
      verify { preferences.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `updateUserAgent trims surrounding whitespace after stripping`() {
      viewModel.updateUserAgent("  Agent/1.0\n  ")
      verify { preferences.saveUserAgent("Agent/1.0") }
    }
  }

  @Nested
  inner class Logout {
    @Test
    fun `logout calls clearPreferences`() {
      viewModel.logout()
      verify { preferences.clearPreferences() }
    }
  }

  @Nested
  inner class AutoDownloadDelayed {
    @Test
    fun `preferAutoDownloadDelayed updates StateFlow`() {
      viewModel.preferAutoDownloadDelayed(true)
      assertTrue(viewModel.autoDownloadDelayed.value == true)
    }

    @Test
    fun `preferAutoDownloadDelayed saves to preferences`() {
      viewModel.preferAutoDownloadDelayed(false)
      verify { preferences.saveAutoDownloadDelayed(false) }
    }
  }

  @Nested
  inner class FetchLibraries {
    @Test
    fun `fetchLibraries populates libraries on success`() {
      val libs =
        listOf(
          Library(id = "l1", title = "Books", type = LibraryType.LIBRARY),
          Library(id = "l2", title = "Podcasts", type = LibraryType.PODCAST),
        )
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)

      viewModel.fetchLibraries()

      assertEquals(libs, viewModel.libraries.value)
    }

    @Test
    fun `fetchLibraries selects matching preferred library`() {
      val preferred = Library(id = "l2", title = "Podcasts", type = LibraryType.PODCAST)
      every { preferences.getPreferredLibrary() } returns preferred
      val libs =
        listOf(
          Library(id = "l1", title = "Books", type = LibraryType.LIBRARY),
          preferred,
        )
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)

      viewModel.fetchLibraries()

      assertEquals(preferred, viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries selects first library when no preferred set`() {
      every { preferences.getPreferredLibrary() } returns null
      val libs =
        listOf(
          Library(id = "l1", title = "Books", type = LibraryType.LIBRARY),
        )
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)

      viewModel.fetchLibraries()

      assertEquals(libs.first(), viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries falls back to cached preferred library on error`() {
      val preferred = Library(id = "l1", title = "Books", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns preferred
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider)

      viewModel.fetchLibraries()

      assertEquals(listOf(preferred), viewModel.libraries.value)
    }
  }
}
