package org.grakovne.lissen.channel.audiobookshelf

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.domain.NetworkType
import org.grakovne.lissen.domain.connection.LocalUrl
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.ui.screens.common.hasLocalNetworkPermission
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudiobookshelfHostProviderTest {
  private val context = mockk<Context>(relaxed = true)
  private val session = mockk<SessionPreferences>(relaxed = true)
  private val connection = mockk<ConnectionPreferences>(relaxed = true)
  private val networkService = mockk<NetworkService>(relaxed = true)

  private lateinit var provider: AudiobookshelfHostProvider

  @BeforeEach
  fun setUp() {
    mockkStatic("org.grakovne.lissen.ui.screens.common.RequestLocalNetworkPermissionKt")
    every { hasLocalNetworkPermission(any()) } returns true

    provider = AudiobookshelfHostProvider(context, session, connection, networkService)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `returns null without a configured host`() {
    every { session.getHost() } returns null

    assertNull(provider.provideHost())
  }

  @Test
  fun `serves the external host when no local routes are configured`() {
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns emptyList()

    assertEquals(Host("https://external.example", HostType.EXTERNAL), provider.provideHost())
  }

  @Test
  fun `serves the external host over cellular`() {
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns listOf(localUrl("HomeWiFi", "http://local.example"))
    every { networkService.getCurrentNetworkType() } returns NetworkType.CELLULAR

    assertEquals(Host("https://external.example", HostType.EXTERNAL), provider.provideHost())
  }

  @Test
  fun `serves the external host when the wifi network cannot be detected`() {
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns listOf(localUrl("HomeWiFi", "http://local.example"))
    every { networkService.getCurrentNetworkType() } returns NetworkType.WIFI
    every { networkService.getCurrentWifiSSID() } returns null

    assertEquals(Host("https://external.example", HostType.EXTERNAL), provider.provideHost())
  }

  @Test
  fun `serves the internal route matching the current ssid case insensitively`() {
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns listOf(localUrl("homewifi", "http://local.example"))
    every { networkService.getCurrentNetworkType() } returns NetworkType.WIFI
    every { networkService.getCurrentWifiSSID() } returns "HomeWiFi"

    assertEquals(Host("http://local.example", HostType.INTERNAL), provider.provideHost())
  }

  @Test
  fun `serves the external host when no route matches the ssid`() {
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns listOf(localUrl("OtherNet", "http://local.example"))
    every { networkService.getCurrentNetworkType() } returns NetworkType.WIFI
    every { networkService.getCurrentWifiSSID() } returns "HomeWiFi"

    assertEquals(Host("https://external.example", HostType.EXTERNAL), provider.provideHost())
  }

  @Test
  fun `falls back to the external host without the local network permission`() {
    every { hasLocalNetworkPermission(any()) } returns false
    every { session.getHost() } returns "https://external.example"
    every { connection.getLocalUrls() } returns listOf(localUrl("HomeWiFi", "http://local.example"))
    every { networkService.getCurrentNetworkType() } returns NetworkType.WIFI
    every { networkService.getCurrentWifiSSID() } returns "HomeWiFi"

    assertEquals(Host("https://external.example", HostType.EXTERNAL), provider.provideHost())
  }

  private fun localUrl(
    ssid: String,
    route: String,
  ) = LocalUrl(ssid = ssid, route = route)
}
