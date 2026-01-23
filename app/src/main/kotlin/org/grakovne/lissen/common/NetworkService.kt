package org.grakovne.lissen.common

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.NetworkType
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
  ) : RunningComponent {
    private val connectivityManager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private var cachedNetworkHandle: Long? = null
    private var cachedSsid: String? = null

    private val _networkStatus = MutableStateFlow(false)
    val networkStatus: StateFlow<Boolean> = _networkStatus

    private val _isServerAvailable = MutableStateFlow(false)
    val isServerAvailable: StateFlow<Boolean> = _isServerAvailable

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
      val networkRequest =
        NetworkRequest
          .Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
          .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
          .build()

      val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onAvailable(network: Network) {
            checkServerAvailability()
          }

          override fun onLost(network: Network) {
            if (cachedNetworkHandle == network.getNetworkHandle()) {
              cachedSsid = null
            }
            checkServerAvailability()
          }

          override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
          ) {
            checkServerAvailability()
          }
        }

      connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
      checkServerAvailability()

      scope.launch {
        preferences.hostFlow.collect {
          checkServerAvailability()
        }
      }
    }

    private var checkJob: Job? = null

    private fun checkServerAvailability() {
      checkJob?.cancel()
      checkJob =
        scope.launch {
          delay(500)
          val isConnectedToInternet = isNetworkAvailable()
          _networkStatus.emit(isConnectedToInternet)

          if (!isConnectedToInternet) {
            _isServerAvailable.emit(false)
            return@launch
          }

          val hostUrl = preferences.getHost()
          if (hostUrl.isNullOrBlank()) {
            _isServerAvailable.emit(false)
            return@launch
          }

          try {
            val url = java.net.URL(hostUrl)
            val port = if (url.port == -1) url.defaultPort else url.port
            val address = java.net.InetSocketAddress(url.host, port)

            java.net.Socket().use { socket ->
              socket.connect(address, 2000)
            }

            _isServerAvailable.emit(true)
          } catch (e: Exception) {
            Timber.e(e, "Server reachability check failed for $hostUrl")
            _isServerAvailable.emit(false)
          }
        }
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

      val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
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

    override fun onDestroy() {
      scope.cancel()
    }
  }
