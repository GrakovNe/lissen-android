package org.grakovne.lissen.common

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.lib.domain.NetworkType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) : RunningComponent {
    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private var cachedNetworkHandle: Long? = null
    private var cachedSsid: String? = null

    override fun onCreate() {
      val networkRequest =
        NetworkRequest
          .Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .build()

      val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onLost(network: Network) {
            if (cachedNetworkHandle == network.getNetworkHandle()) {
              cachedSsid = null
            }
          }
        }

      connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

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

      if (ssid == "<unknown ssid>") {
        Timber.d("Using cached value $cachedSsid because the actual SSID cannot be checked")
        return cachedSsid
      }

      val networkSsid = ssid.removeSurrounding("\"")

      cachedSsid = networkSsid
      cachedNetworkHandle = network.networkHandle
      return cachedSsid
    }
  }
