package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.channel.audiobookshelf.Host
import org.grakovne.lissen.channel.audiobookshelf.common.api.ConditionalCache
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.channel.common.DEFAULT_USER_AGENT
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.domain.connection.ServerRequestHeader
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.PreferencesReset
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.service.OfflineSessionSyncService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionSettingsViewModelTest {
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val preferencesReset = mockk<PreferencesReset>(relaxed = true)
  private val offlineSessionSyncService = mockk<OfflineSessionSyncService>(relaxed = true)
  private val mediaChannel = mockk<LissenMediaProvider>(relaxed = true)
  private val conditionalCache = mockk<ConditionalCache>(relaxed = true)
  private lateinit var viewModel: ConnectionSettingsViewModel

  private fun buildViewModel() =
    ConnectionSettingsViewModel(mediaChannel, session, connection, preferencesReset, offlineSessionSyncService, conditionalCache)

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    every { session.getHost() } returns "http://example.com"
    every { session.getUsername() } returns "user"
    every { session.getServerVersion() } returns "1.0.0"
    every { connection.getCustomHeaders() } returns emptyList()
    every { connection.getLocalUrls() } returns emptyList()
    every { connection.getSslBypass() } returns false
    every { connection.getUserAgent() } returns DEFAULT_USER_AGENT
    every { connection.clientCertAliasFlow } returns flowOf(null)
    every { mediaChannel.fetchConnectionHost() } returns OperationResult.Error(OperationError.NetworkError)

    viewModel = buildViewModel()
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class SslBypass {
    @Test
    fun `preferBypassSsl updates StateFlow`() {
      viewModel.preferBypassSsl(true)
      assertTrue(viewModel.bypassSsl.value)
    }

    @Test
    fun `preferBypassSsl saves to preferences`() {
      viewModel.preferBypassSsl(false)
      verify { connection.saveSslBypass(false) }
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
      verify { connection.saveLocalUrls(match { saved -> saved.none { it.ssid.isEmpty() } }) }
    }

    @Test
    fun `updateLocalUrls keeps entries with valid ssid and route`() {
      val urls =
        listOf(
          LocalUrl(ssid = "HomeWifi", route = "http://192.168.1.1"),
          LocalUrl(ssid = "WorkWifi", route = "http://10.0.0.1"),
        )
      viewModel.updateLocalUrls(urls)
      verify { connection.saveLocalUrls(match { it.size == 2 }) }
    }

    @Test
    fun `updateLocalUrls deduplicates by ssid`() {
      val urls =
        listOf(
          LocalUrl(ssid = "WiFi", route = "http://192.168.1.1"),
          LocalUrl(ssid = "WiFi", route = "http://192.168.1.2"),
        )
      viewModel.updateLocalUrls(urls)
      verify { connection.saveLocalUrls(match { it.size == 1 }) }
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
      verify { connection.saveCustomHeaders(match { saved -> saved.none { it.name.isEmpty() } }) }
    }

    @Test
    fun `updateCustomHeaders filters out entries with empty value`() {
      val headers =
        listOf(
          ServerRequestHeader(name = "X-Token", value = ""),
          ServerRequestHeader(name = "X-Key", value = "abc"),
        )
      viewModel.updateCustomHeaders(headers)
      verify { connection.saveCustomHeaders(match { saved -> saved.none { it.value.isEmpty() } }) }
    }

    @Test
    fun `updateCustomHeaders deduplicates by name`() {
      val headers =
        listOf(
          ServerRequestHeader(name = "X-Token", value = "first"),
          ServerRequestHeader(name = "X-Token", value = "second"),
        )
      viewModel.updateCustomHeaders(headers)
      verify { connection.saveCustomHeaders(match { it.size == 1 }) }
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

      assertEquals("StoredAgent/3.0", buildViewModel().userAgent.value)
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

    @Test
    fun `logout drops the offline rows after the account is cleared`() {
      viewModel.logout()

      verifyOrder {
        preferencesReset.clearAll()
        offlineSessionSyncService.dropAllSessions()
      }
    }
  }

  @Nested
  inner class ConnectionInfoRefresh {
    private val info = ConnectionInfo(username = "alice", serverVersion = "2.0.0", buildNumber = "42")

    @Test
    fun `refreshConnectionInfo updates username and server version on success`() {
      coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Success(info)

      viewModel.refreshConnectionInfo()

      assertEquals("alice", viewModel.username.value)
      assertEquals("2.0.0", viewModel.serverVersion.value)
    }

    @Test
    fun `refreshConnectionInfo caches username and server version to preferences on success`() {
      coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Success(info)

      viewModel.refreshConnectionInfo()

      verify { session.saveUsername("alice") }
      verify { session.saveServerVersion("2.0.0") }
    }

    @Test
    fun `refreshConnectionInfo leaves username and server version untouched on error`() {
      coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Error(OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals("user", viewModel.username.value)
      assertEquals("1.0.0", viewModel.serverVersion.value)
    }

    @Test
    fun `refreshConnectionInfo updates host from the channel on success`() {
      val host = Host.internal("http://10.0.0.1")
      every { mediaChannel.fetchConnectionHost() } returns OperationResult.Success(host)
      coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Error(OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals(host, viewModel.host.value)
    }

    @Test
    fun `refreshConnectionInfo falls back to the cached host from preferences on error`() {
      every { mediaChannel.fetchConnectionHost() } returns OperationResult.Error(OperationError.NetworkError)
      every { session.getHost() } returns "http://cached.example.com"
      coEvery { mediaChannel.fetchConnectionInfo() } returns OperationResult.Error(OperationError.NetworkError)

      viewModel.refreshConnectionInfo()

      assertEquals(Host.external("http://cached.example.com"), viewModel.host.value)
    }
  }

  @Nested
  inner class Delegates {
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
    fun `hasCredentials delegates to preferences`() {
      every { session.hasCredentials() } returns true

      assertTrue(viewModel.hasCredentials())
    }
  }
}
