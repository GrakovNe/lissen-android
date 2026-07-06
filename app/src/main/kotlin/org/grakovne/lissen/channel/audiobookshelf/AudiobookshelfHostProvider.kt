package org.grakovne.lissen.channel.audiobookshelf

import android.content.Context
import androidx.annotation.Keep
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.domain.NetworkType
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.ui.screens.common.hasLocalNetworkPermission
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfHostProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionPreferences,
    private val connection: ConnectionPreferences,
    private val networkService: NetworkService,
  ) {
    fun provideHost(): Host? {
      val externalHost =
        session
          .getHost()
          ?.let(Host.Companion::external)
          ?: return null

      if (connection.getLocalUrls().isEmpty()) {
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

      return connection
        .getLocalUrls()
        .find { it.ssid.equals(currentNetwork, ignoreCase = true) }
        ?.route
        ?.takeIf { localAccessGranted(context, it) }
        ?.let(Host.Companion::internal)
        ?.also { Timber.d("Using internal host: ${it.url}") }
        ?: externalHost.also { Timber.d("Using external host: ${it.url}, no internal matches") }
    }

    companion object {
      private fun localAccessGranted(
        context: Context,
        url: String,
      ): Boolean = hasLocalNetworkPermission(context)
    }
  }

@Keep
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
