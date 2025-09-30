package org.grakovne.lissen.common

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.lib.domain.NetworkType
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkQualityService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isNetworkAvailable(): Boolean {
      val network = connectivityManager.activeNetwork ?: return false

      val networkCapabilities =
        connectivityManager
          .getNetworkCapabilities(network)
          ?: return false

      return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun getCurrentNetworkType(): NetworkType? {
      val network = connectivityManager.activeNetwork ?: return null
      val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
      
      return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        else -> null
      }
    }

    fun getCurrentWifiSSID(): String? {
      val network = connectivityManager.activeNetwork ?: return null
      val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null

      if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
      val wifiInfo = wifiManager.connectionInfo
      val ssid = wifiInfo.ssid

      return if (ssid != "<unknown ssid>") ssid.removeSurrounding("\"") else null
    }
  }
