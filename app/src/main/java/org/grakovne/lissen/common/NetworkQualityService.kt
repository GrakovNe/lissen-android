package org.grakovne.lissen.common

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
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
  }
