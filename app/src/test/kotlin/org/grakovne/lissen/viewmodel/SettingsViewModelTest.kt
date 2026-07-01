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
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
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
  private val configProvider = mockk<LissenConfigProvider>(relaxed = true)
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

    viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)
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
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, true)

      assertTrue(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType removes type when state is false`() {
      every { preferences.getAutoDownloadLibraryTypes() } returns LibraryType.meaningfulTypes
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)

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
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)
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
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)

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
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)

      viewModel.fetchLibraries()

      assertEquals(libs.first(), viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries falls back to cached preferred library on error`() {
      val preferred = Library(id = "l1", title = "Books", type = LibraryType.LIBRARY)
      every { preferences.getPreferredLibrary() } returns preferred
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)
      viewModel = SettingsViewModel(mediaChannel, preferences, logProvider, configProvider)

      viewModel.fetchLibraries()

      assertEquals(listOf(preferred), viewModel.libraries.value)
    }
  }

  @Nested
  inner class ConnectionInfo {
    @Test
    fun `refreshConnectionInfo updates username and server version on success`() {
      val info =
        org.grakovne.lissen.channel.common.ConnectionInfo(
          username = "alice",
          serverVersion = "2.0.0",
          buildNumber = "42",
        )
      io.mockk.coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Success(info)

      viewModel.refreshConnectionInfo()

      assertEquals("alice", viewModel.username.value)
      assertEquals("2.0.0", viewModel.serverVersion.value)
    }

    @Test
    fun `refreshConnectionInfo caches username and server version to preferences on success`() {
      val info =
        org.grakovne.lissen.channel.common.ConnectionInfo(
          username = "alice",
          serverVersion = "2.0.0",
          buildNumber = "42",
        )
      io.mockk.coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Success(info)

      viewModel.refreshConnectionInfo()

      verify { preferences.saveUsername("alice") }
      verify { preferences.saveServerVersion("2.0.0") }
    }

    @Test
    fun `refreshConnectionInfo leaves username and server version untouched on error`() {
      io.mockk.coEvery { mediaChannel.fetchConnectionInfo() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals("user", viewModel.username.value)
      assertEquals("1.0.0", viewModel.serverVersion.value)
    }

    @Test
    fun `refreshConnectionInfo updates host from the channel on success`() {
      val host =
        org.grakovne.lissen.channel.audiobookshelf.Host
          .internal("http://10.0.0.1")
      every { mediaChannel.fetchConnectionHost() } returns OperationResult.Success(host)
      io.mockk.coEvery { mediaChannel.fetchConnectionInfo() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals(host, viewModel.host.value)
    }

    @Test
    fun `refreshConnectionInfo falls back to the cached host from preferences on error`() {
      every { mediaChannel.fetchConnectionHost() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)
      every { preferences.getHost() } returns "http://cached.example.com"
      io.mockk.coEvery { mediaChannel.fetchConnectionInfo() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals(
        org.grakovne.lissen.channel.audiobookshelf.Host
          .external("http://cached.example.com"),
        viewModel.host.value,
      )
    }
  }

  @Nested
  inner class HideCompletedToggle {
    @Test
    fun `toggleHideCompleted saves true when currently false`() {
      every { preferences.getHideCompleted() } returns false

      viewModel.toggleHideCompleted()

      verify { preferences.saveHideCompleted(true) }
    }

    @Test
    fun `toggleHideCompleted saves false when currently true`() {
      every { preferences.getHideCompleted() } returns true

      viewModel.toggleHideCompleted()

      verify { preferences.saveHideCompleted(false) }
    }
  }

  @Nested
  inner class MiscPreferences {
    @Test
    fun `preferLibraryGrouping saves the grouping to preferences`() {
      viewModel.preferLibraryGrouping(org.grakovne.lissen.common.LibraryGrouping.SERIES)

      verify { preferences.saveLibraryGrouping(org.grakovne.lissen.common.LibraryGrouping.SERIES) }
    }

    @Test
    fun `saveClientCertAlias delegates to preferences`() {
      viewModel.saveClientCertAlias("alias-1")

      verify { preferences.saveClientCertAlias("alias-1") }
    }

    @Test
    fun `clearClientCertAlias delegates to preferences`() {
      viewModel.clearClientCertAlias()

      verify { preferences.clearClientCertAlias() }
    }

    @Test
    fun `saveDefaultTimerOption updates StateFlow and preferences`() {
      val option =
        org.grakovne.lissen.domain
          .DurationTimerOption(600)

      viewModel.saveDefaultTimerOption(option)

      assertEquals(option, viewModel.defaultTimerOption.value)
      verify { preferences.saveDefaultTimerOption(option) }
    }

    @Test
    fun `preferAudioFocusLossPolicy updates StateFlow and preferences`() {
      viewModel.preferAudioFocusLossPolicy(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME)

      assertEquals(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME, viewModel.audioFocusLossPolicy.value)
      verify { preferences.saveAudioFocusLossPolicy(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME) }
    }

    @Test
    fun `preferSoftwareCodecsEnabled updates StateFlow and preferences`() {
      viewModel.preferSoftwareCodecsEnabled(true)

      assertTrue(viewModel.softwareCodecsEnabled.value)
      verify { preferences.saveSoftwareCodecsEnabled(true) }
    }

    @Test
    fun `preferActivityLoggingEnabled true enables logging`() {
      viewModel.preferActivityLoggingEnabled(true)

      assertTrue(viewModel.activityLoggingEnabled.value)
      verify { logProvider.enableLogging() }
    }

    @Test
    fun `preferActivityLoggingEnabled false disables logging`() {
      viewModel.preferActivityLoggingEnabled(false)

      assertFalse(viewModel.activityLoggingEnabled.value)
      verify { logProvider.disableLogging() }
    }

    @Test
    fun `preferAutoDownloadOption updates StateFlow and preferences`() {
      viewModel.preferAutoDownloadOption(org.grakovne.lissen.domain.CurrentItemDownloadOption)

      assertEquals(org.grakovne.lissen.domain.CurrentItemDownloadOption, viewModel.preferredAutoDownloadOption.value)
      verify { preferences.saveAutoDownloadOption(org.grakovne.lissen.domain.CurrentItemDownloadOption) }
    }

    @Test
    fun `hasCredentials delegates to preferences`() {
      every { preferences.hasCredentials() } returns true

      assertTrue(viewModel.hasCredentials())
    }

    @Test
    fun `fetchPreferredLibraryId returns the preferred library id`() {
      every { preferences.getPreferredLibrary() } returns Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)

      assertEquals("lib-1", viewModel.fetchPreferredLibraryId())
    }

    @Test
    fun `fetchPreferredLibraryId returns empty string when no preferred library`() {
      every { preferences.getPreferredLibrary() } returns null

      assertEquals("", viewModel.fetchPreferredLibraryId())
    }

    @Test
    fun `fetchLibraryOrdering delegates to preferences`() {
      val ordering = LibraryOrderingConfiguration(LibraryOrderingOption.AUTHOR, LibraryOrderingDirection.DESCENDING)
      every { preferences.getLibraryOrdering() } returns ordering

      assertEquals(ordering, viewModel.fetchLibraryOrdering())
    }

    @Test
    fun `provideLogArchive delegates to the log provider`() {
      val file = java.io.File("archive.log")
      every { logProvider.archiveLogFile() } returns file

      assertEquals(file, viewModel.provideLogArchive())
    }
  }

  @Nested
  inner class ConfigBackup {
    @Test
    fun `provideConfigArchive delegates to the config provider`() {
      val file = java.io.File("lissen-settings.json")
      every { configProvider.exportConfigFile() } returns file

      assertEquals(file, viewModel.provideConfigArchive())
    }

    @Test
    fun `importSettingsJson returns true when the config provider imports successfully`() {
      every { configProvider.importConfig(any()) } returns true

      assertTrue(viewModel.importSettingsJson("{}"))
    }

    @Test
    fun `importSettingsJson returns false when the config provider rejects the input`() {
      every { configProvider.importConfig(any()) } returns false

      assertFalse(viewModel.importSettingsJson("not valid json"))
    }
  }
}
