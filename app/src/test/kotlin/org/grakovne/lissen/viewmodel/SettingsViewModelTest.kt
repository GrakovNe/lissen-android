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
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.ColorScheme
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.logging.LissenLogProvider
import org.grakovne.lissen.persistence.preferences.AppearancePreferences
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.DiagnosticsPreferences
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.LissenConfigProvider
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.EqualizerBandProvider
import org.grakovne.lissen.playback.EqualizerCapabilities
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
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val libraryPreferences = mockk<LibraryPreferences>(relaxed = true)
  private val appearance = mockk<AppearancePreferences>(relaxed = true)
  private val playback = mockk<PlaybackPreferences>(relaxed = true)
  private val download = mockk<DownloadPreferences>(relaxed = true)
  private val diagnostics = mockk<DiagnosticsPreferences>(relaxed = true)
  private val preferencesReset = mockk<PreferencesReset>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val logProvider = mockk<LissenLogProvider>(relaxed = true)
  private val configProvider = mockk<LissenConfigProvider>(relaxed = true)
  private val equalizerBandProvider = mockk<EqualizerBandProvider>(relaxed = true)
  private lateinit var viewModel: SettingsViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(testDispatcher)

    every { session.getHost() } returns "http://example.com"
    every { session.getUsername() } returns "user"
    every { session.getServerVersion() } returns "1.0.0"
    every { libraryPreferences.getPreferredLibrary() } returns null
    every { appearance.getColorScheme() } returns ColorScheme.FOLLOW_SYSTEM
    every { appearance.getMaterialYouColors() } returns false
    every { download.getAutoDownloadNetworkType() } returns NetworkTypeAutoCache.WIFI_ONLY
    every { download.getAutoDownloadLibraryTypes() } returns LibraryType.meaningfulTypes
    every { download.getAutoDownloadOption() } returns null
    every { playback.getPlaybackVolumeBoost() } returns 0
    every { libraryPreferences.getLibraryOrdering() } returns LibraryOrderingConfiguration.default
    every { connection.getCustomHeaders() } returns emptyList()
    every { connection.getLocalUrls() } returns emptyList()
    every { playback.getSeekTime() } returns SeekTime.Default
    every { playback.getEqualizer() } returns EqualizerSettings.Default
    coEvery { equalizerBandProvider.getCapabilities() } returns EqualizerCapabilities.Unavailable
    every { diagnostics.getAcraEnabled() } returns true
    every { connection.getSslBypass() } returns false
    every { playback.getSoftwareCodecsEnabled() } returns false
    every { diagnostics.isActivityLoggingEnabled() } returns true
    every { download.getAutoDownloadDelayed() } returns false
    every { connection.getUserAgent() } returns DEFAULT_USER_AGENT
    every { connection.clientCertAliasFlow } returns flowOf(null)
    every { libraryPreferences.hideCompletedFlow } returns flowOf(false)
    every { mediaChannel.fetchConnectionHost() } returns
      OperationResult.Error(
        org.grakovne.lissen.channel.common.OperationError.NetworkError,
      )

    viewModel =
      SettingsViewModel(
        mediaChannel,
        session,
        connection,
        libraryPreferences,
        appearance,
        playback,
        download,
        diagnostics,
        preferencesReset,
        logProvider,
        configProvider,
        equalizerBandProvider,
      )
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
      verify { libraryPreferences.savePreferredLibrary(library) }
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
      verify { appearance.saveColorScheme(ColorScheme.LIGHT) }
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
      verify { appearance.saveMaterialYouColors(false) }
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
      verify { download.saveAutoDownloadNetworkType(NetworkTypeAutoCache.WIFI_ONLY) }
    }
  }

  @Nested
  inner class AutoDownloadLibraryType {
    @Test
    fun `changeAutoDownloadLibraryType adds type when state is true`() {
      every { download.getAutoDownloadLibraryTypes() } returns listOf(LibraryType.LIBRARY)
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, true)

      assertTrue(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType removes type when state is false`() {
      every { download.getAutoDownloadLibraryTypes() } returns LibraryType.meaningfulTypes
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )

      viewModel.changeAutoDownloadLibraryType(LibraryType.PODCAST, false)

      assertFalse(viewModel.preferredAutoDownloadLibraryTypes.value.contains(LibraryType.PODCAST))
    }

    @Test
    fun `changeAutoDownloadLibraryType saves updated list to preferences`() {
      viewModel.changeAutoDownloadLibraryType(LibraryType.LIBRARY, false)
      verify { download.saveAutoDownloadLibraryTypes(any()) }
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
      verify { libraryPreferences.saveLibraryOrdering(config) }
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
      verify { playback.savePlaybackVolumeBoost(6) }
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
      verify { diagnostics.saveAcraEnabled(true) }
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
      verify { connection.saveSslBypass(false) }
    }
  }

  @Nested
  inner class SeekTimePreference {
    @Test
    fun `preferForward updates seek forward`() {
      viewModel.preferForward(60)
      assertEquals(60, viewModel.seekTime.value.forward)
    }

    @Test
    fun `preferRewind updates seek rewind`() {
      viewModel.preferRewind(30)
      assertEquals(30, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferForward preserves rewind value`() {
      viewModel.preferForward(60)
      assertEquals(SeekTime.Default.rewind, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferRewind preserves forward value`() {
      viewModel.preferRewind(10)
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
        connection.saveLocalUrls(
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
        connection.saveLocalUrls(match { it.size == 2 })
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
        connection.saveLocalUrls(match { it.size == 1 })
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
        connection.saveCustomHeaders(
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
        connection.saveCustomHeaders(
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
        connection.saveCustomHeaders(match { it.size == 1 })
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
      verify { connection.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `resetUserAgent calls clearUserAgent on preferences`() {
      viewModel.resetUserAgent()
      verify { connection.clearUserAgent() }
    }

    @Test
    fun `resetUserAgent restores StateFlow to DEFAULT_USER_AGENT`() {
      viewModel.updateUserAgent("CustomAgent/1.0")
      viewModel.resetUserAgent()
      assertEquals(DEFAULT_USER_AGENT, viewModel.userAgent.value)
    }

    @Test
    fun `userAgent StateFlow is initialized from preferences`() {
      every { connection.getUserAgent() } returns "StoredAgent/3.0"
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )
      assertEquals("StoredAgent/3.0", viewModel.userAgent.value)
    }

    @Test
    fun `updateUserAgent strips newline characters`() {
      viewModel.updateUserAgent("Custom\nAgent/1.0")
      verify { connection.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `updateUserAgent strips carriage return characters`() {
      viewModel.updateUserAgent("Custom\rAgent/1.0")
      verify { connection.saveUserAgent("CustomAgent/1.0") }
    }

    @Test
    fun `updateUserAgent trims surrounding whitespace after stripping`() {
      viewModel.updateUserAgent("  Agent/1.0\n  ")
      verify { connection.saveUserAgent("Agent/1.0") }
    }
  }

  @Nested
  inner class Logout {
    @Test
    fun `logout calls clearPreferences`() {
      viewModel.logout()
      verify { preferencesReset.clearAll() }
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
      verify { download.saveAutoDownloadDelayed(false) }
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
      every { libraryPreferences.getPreferredLibrary() } returns preferred
      val libs =
        listOf(
          Library(id = "l1", title = "Books", type = LibraryType.LIBRARY),
          preferred,
        )
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )

      viewModel.fetchLibraries()

      assertEquals(preferred, viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries selects first library when no preferred set`() {
      every { libraryPreferences.getPreferredLibrary() } returns null
      val libs =
        listOf(
          Library(id = "l1", title = "Books", type = LibraryType.LIBRARY),
        )
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns OperationResult.Success(libs)
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )

      viewModel.fetchLibraries()

      assertEquals(libs.first(), viewModel.preferredLibrary.value)
    }

    @Test
    fun `fetchLibraries falls back to cached preferred library on error`() {
      val preferred = Library(id = "l1", title = "Books", type = LibraryType.LIBRARY)
      every { libraryPreferences.getPreferredLibrary() } returns preferred
      io.mockk.coEvery { mediaChannel.fetchLibraries() } returns
        OperationResult.Error(org.grakovne.lissen.channel.common.OperationError.NetworkError)
      viewModel =
        SettingsViewModel(
          mediaChannel,
          session,
          connection,
          libraryPreferences,
          appearance,
          playback,
          download,
          diagnostics,
          preferencesReset,
          logProvider,
          configProvider,
          equalizerBandProvider,
        )

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

      verify { session.saveUsername("alice") }
      verify { session.saveServerVersion("2.0.0") }
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
      every { session.getHost() } returns "http://cached.example.com"
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

  @Nested
  inner class MiscPreferences {
    @Test
    fun `preferLibraryGrouping saves the grouping to preferences`() {
      viewModel.preferLibraryGrouping(org.grakovne.lissen.common.LibraryGrouping.SERIES)

      verify { libraryPreferences.saveLibraryGrouping(org.grakovne.lissen.common.LibraryGrouping.SERIES) }
    }

    @Test
    fun `saveClientCertAlias delegates to preferences`() {
      viewModel.saveClientCertAlias("alias-1")

      verify { connection.saveClientCertAlias("alias-1") }
    }

    @Test
    fun `clearClientCertAlias delegates to preferences`() {
      viewModel.clearClientCertAlias()

      verify { connection.clearClientCertAlias() }
    }

    @Test
    fun `saveDefaultTimerOption updates StateFlow and preferences`() {
      val option =
        org.grakovne.lissen.domain
          .DurationTimerOption(600)

      viewModel.saveDefaultTimerOption(option)

      assertEquals(option, viewModel.defaultTimerOption.value)
      verify { playback.saveDefaultTimerOption(option) }
    }

    @Test
    fun `preferAudioFocusLossPolicy updates StateFlow and preferences`() {
      viewModel.preferAudioFocusLossPolicy(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME)

      assertEquals(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME, viewModel.audioFocusLossPolicy.value)
      verify { playback.saveAudioFocusLossPolicy(org.grakovne.lissen.common.AudioFocusLossPolicy.LOWER_VOLUME) }
    }

    @Test
    fun `preferSoftwareCodecsEnabled updates StateFlow and preferences`() {
      viewModel.preferSoftwareCodecsEnabled(true)

      assertTrue(viewModel.softwareCodecsEnabled.value)
      verify { playback.saveSoftwareCodecsEnabled(true) }
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
      verify { download.saveAutoDownloadOption(org.grakovne.lissen.domain.CurrentItemDownloadOption) }
    }

    @Test
    fun `hasCredentials delegates to preferences`() {
      every { session.hasCredentials() } returns true

      assertTrue(viewModel.hasCredentials())
    }

    @Test
    fun `fetchPreferredLibraryId returns the preferred library id`() {
      every { libraryPreferences.getPreferredLibrary() } returns Library(id = "lib-1", title = "Books", type = LibraryType.LIBRARY)

      assertEquals("lib-1", viewModel.fetchPreferredLibraryId())
    }

    @Test
    fun `fetchPreferredLibraryId returns empty string when no preferred library`() {
      every { libraryPreferences.getPreferredLibrary() } returns null

      assertEquals("", viewModel.fetchPreferredLibraryId())
    }

    @Test
    fun `fetchLibraryOrdering delegates to preferences`() {
      val ordering = LibraryOrderingConfiguration(LibraryOrderingOption.AUTHOR, LibraryOrderingDirection.DESCENDING)
      every { libraryPreferences.getLibraryOrdering() } returns ordering

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
