package org.grakovne.lissen.channel.audiobookshelf

import android.util.Log
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfHostProvider
@Inject
constructor(
  private val sharedPreferences: LissenSharedPreferences,
  private val networkQualityService: NetworkQualityService,
) {
  fun provideHost(): Host? {
    val externalHost =
      sharedPreferences
        .getHost()
        ?.let(Host.Companion::external)
        ?: return null
    
    val currentNetwork = networkQualityService
      .getCurrentWifiSSID()
      ?: return externalHost.also {  Log.d(TAG, "Using external host: ${externalHost.url}, can't detect WiFi network")  }
    
    return sharedPreferences
      .getLocalUrls()
      .find { it.ssid == currentNetwork }
      ?.route
      ?.let(Host.Companion::internal)
      ?.also { Log.d(TAG, "Using internal host: ${it.url}") }
      ?: externalHost.also { Log.d(TAG, "Using external host: ${it.url}, no internal matches") }
  }
  
  companion object {
    private val TAG = "AudiobookshelfHostProvider"
  }
}

enum class HostType {
  INTERNAL,
  EXTERNAL,
}

data class Host(
  val url: String,
  val type: HostType,
) {
  companion object {
    fun external(url: String) = Host(url, HostType.EXTERNAL)
    
    fun internal(url: String) = Host(url, HostType.INTERNAL)
  }
}
