package org.grakovne.lissen.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.NetworkType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NetworkServiceTest {
  private val connectivityManager = mockk<ConnectivityManager>()
  private val wifiManager = mockk<WifiManager>()
  private val wifiInfo = mockk<WifiInfo>(relaxed = true)

  private val context =
    mockk<Context> {
      every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
      every { applicationContext } returns this
      every { getSystemService(Context.WIFI_SERVICE) } returns wifiManager
    }

  private lateinit var networkService: NetworkService

  @BeforeEach
  fun setUp() {
    networkService = NetworkService(context)
  }

  private fun capabilities(
    wifi: Boolean = false,
    cellular: Boolean = false,
    internet: Boolean = true,
  ) = mockk<NetworkCapabilities> {
    every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns wifi
    every { hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns cellular
    every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns internet
  }

  @Nested
  inner class Availability {
    @Test
    fun `no active network means no connectivity`() {
      every { connectivityManager.activeNetwork } returns null

      assertFalse(networkService.isNetworkAvailable())
    }

    @Test
    fun `missing capabilities mean no connectivity`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns null

      assertFalse(networkService.isNetworkAvailable())
    }

    @Test
    fun `internet capability means connectivity`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities()

      assertTrue(networkService.isNetworkAvailable())
    }
  }

  @Nested
  inner class NetworkTypeDetection {
    @Test
    fun `detects wifi`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(wifi = true)

      assertEquals(NetworkType.WIFI, networkService.getCurrentNetworkType())
    }

    @Test
    fun `detects cellular`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(cellular = true)

      assertEquals(NetworkType.CELLULAR, networkService.getCurrentNetworkType())
    }

    @Test
    fun `returns null for other transports`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities()

      assertNull(networkService.getCurrentNetworkType())
    }

    @Test
    fun `returns null without an active network`() {
      every { connectivityManager.activeNetwork } returns null

      assertNull(networkService.getCurrentNetworkType())
    }
  }

  @Nested
  inner class WifiSsid {
    @Test
    fun `returns null when the active network is not wifi`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(cellular = true)

      assertNull(networkService.getCurrentWifiSSID())
    }

    @Test
    fun `strips quotes from the reported ssid`() {
      val network = mockk<Network>()
      every { network.networkHandle } returns 7L
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(wifi = true)
      every { wifiManager.connectionInfo } returns wifiInfo
      every { wifiInfo.ssid } returns "\"HomeNet\""

      assertEquals("HomeNet", networkService.getCurrentWifiSSID())
    }

    @Test
    fun `falls back to the cached ssid when the system hides it`() {
      val network = mockk<Network>()
      every { network.networkHandle } returns 7L
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(wifi = true)
      every { wifiManager.connectionInfo } returns wifiInfo

      every { wifiInfo.ssid } returns "\"HomeNet\""
      assertEquals("HomeNet", networkService.getCurrentWifiSSID())

      every { wifiInfo.ssid } returns "<unknown ssid>"
      assertEquals("HomeNet", networkService.getCurrentWifiSSID())
    }

    @Test
    fun `unknown ssid without a cache is null`() {
      val network = mockk<Network>()
      every { connectivityManager.activeNetwork } returns network
      every { connectivityManager.getNetworkCapabilities(network) } returns capabilities(wifi = true)
      every { wifiManager.connectionInfo } returns wifiInfo
      every { wifiInfo.ssid } returns "<unknown ssid>"

      assertNull(networkService.getCurrentWifiSSID())
    }
  }
}
