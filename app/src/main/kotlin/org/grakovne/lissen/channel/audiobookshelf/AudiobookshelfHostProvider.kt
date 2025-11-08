package org.grakovne.lissen.channel.audiobookshelf

import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.lib.domain.NetworkType
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfHostProvider
  @Inject
  constructor(
    private val sharedPreferences: LissenSharedPreferences,
    private val networkService: NetworkService,
  ) {
    fun provideHost(): Host? {
      val externalHost =
        sharedPreferences
          .getHost()
          ?.let(Host.Companion::external)
          ?: return null

      if (sharedPreferences.getLocalUrls().isEmpty()) {
        Timber.d("Using external host: ${externalHost.url}, no local routes")
        return externalHost
      }

      if (networkService.getCurrentNetworkType() == NetworkType.CELLULAR) {
        Timber.d("Using external host: ${externalHost.url}, no WiFi connection")
        return externalHost
      }

      val currentNetwork =
        networkService
          .getCurrentWifiSSID()
          ?: return externalHost.also { Timber.d("Using external host: ${externalHost.url}, can't detect WiFi network") }

      return sharedPreferences
        .getLocalUrls()
        .find { it.ssid.equals(currentNetwork, ignoreCase = true) }
        ?.route
        ?.let(Host.Companion::internal)
        ?.also { Timber.d("Using internal host: ${it.url}") }
        ?: externalHost.also { Timber.d("Using external host: ${it.url}, no internal matches") }
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
