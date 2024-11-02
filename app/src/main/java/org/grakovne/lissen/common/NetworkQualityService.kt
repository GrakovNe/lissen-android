package org.grakovne.lissen.common

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkQualityService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val connectivityManager =
        context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkStatus = MutableStateFlow(false)
    val networkStatus: StateFlow<Boolean> = _networkStatus.asStateFlow()

    init {
        registerNetworkCallback()
    }

    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _networkStatus.value = true
            }

            override fun onLost(network: Network) {
                _networkStatus.value = false
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }
}
