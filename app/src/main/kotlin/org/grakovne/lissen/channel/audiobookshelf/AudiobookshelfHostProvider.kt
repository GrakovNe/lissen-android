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
    /** A local route that answered on a Wi-Fi network whose name is hidden, valid only on that network. */
    @Volatile
    private var confirmedRoute: ConfirmedRoute? = null

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
          ?: return confirmedHost()
            ?: externalHost.also { Timber.d("Using external host: ${externalHost.url}, can't detect WiFi network") }

      return connection
        .getLocalUrls()
        .find { it.ssid.equals(currentNetwork, ignoreCase = true) }
        ?.route
        ?.takeIf { localAccessGranted(context, it) }
        ?.let(Host.Companion::internal)
        ?.also { Timber.d("Using internal host: ${it.url}") }
        ?: externalHost.also { Timber.d("Using external host: ${it.url}, no internal matches") }
    }

    /**
     * The local routes worth trying on the current network, because Android hides the Wi-Fi name
     * from an app in the background and [provideHost] cannot match a route by name. Empty on
     * cellular, on a Wi-Fi network whose name is known, or without the local network permission.
     */
    fun routesForUnnamedWifi(): List<String> {
      if (session.getHost() == null) return emptyList()

      val routes = connection.getLocalUrls()
      if (routes.isEmpty()) return emptyList()
      if (networkService.getCurrentNetworkType() == NetworkType.CELLULAR) return emptyList()
      if (networkService.getCurrentWifiSSID() != null) return emptyList()

      return routes
        .map { it.route }
        .filter { localAccessGranted(context, it) }
        .distinct()
    }

    /** Uses [route] on the current network until the network changes; null forgets it. */
    fun confirmRoute(route: String?) {
      confirmedRoute =
        route
          ?.let { networkService.getCurrentNetworkHandle()?.let { handle -> ConfirmedRoute(handle, it) } }
    }

    private fun confirmedHost(): Host? {
      val confirmed = confirmedRoute ?: return null
      if (confirmed.networkHandle != networkService.getCurrentNetworkHandle()) return null
      if (connection.getLocalUrls().none { it.route == confirmed.route }) return null
      if (localAccessGranted(context, confirmed.route).not()) return null

      return Host
        .internal(confirmed.route)
        .also { Timber.d("Using internal host: ${it.url}, confirmed on this network") }
    }

    private data class ConfirmedRoute(
      val networkHandle: Long,
      val route: String,
    )

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
