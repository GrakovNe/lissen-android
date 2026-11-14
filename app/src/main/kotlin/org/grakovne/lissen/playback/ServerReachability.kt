package org.grakovne.lissen.playback

import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfHostProvider
import org.grakovne.lissen.channel.audiobookshelf.HostType
import org.grakovne.lissen.content.LissenMediaProvider
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

fun interface ServerReachability {
  suspend fun isReachable(): Boolean
}

/**
 * Asks the server over the route playback would use: a registered local URL on its Wi-Fi network, the external one elsewhere.
 *
 * Android hides the Wi-Fi name from an app in the background, so after rejoining a home network
 * the host provider cannot tell it is home and picks the external URL. When that fails on a Wi-Fi
 * network with no known name, each registered local URL gets a try, and the first that answers is
 * kept for this network, so playback resumes through it.
 */
@Singleton
class MediaProviderServerReachability
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val hostProvider: AudiobookshelfHostProvider,
  ) : ServerReachability {
    override suspend fun isReachable(): Boolean {
      val tried = hostProvider.provideHost()
      if (answers()) return true

      val routes = hostProvider.routesForUnnamedWifi()
      if (routes.isEmpty()) return false

      // a confirmed route stopped answering: the external URL gets its turn again
      hostProvider.confirmRoute(null)
      if (tried?.type == HostType.INTERNAL && answers()) return true

      routes
        .filterNot { tried?.type == HostType.INTERNAL && it == tried.url }
        .forEach { route ->
          hostProvider.confirmRoute(route)

          if (answers()) {
            Timber.d("Server answers on local route $route; Wi-Fi name unknown")
            return true
          }
        }

      hostProvider.confirmRoute(null)
      return false
    }

    private suspend fun answers(): Boolean = mediaProvider.fetchConnectionInfo().fold(onSuccess = { true }, onFailure = { false })
  }
