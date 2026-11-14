package org.grakovne.lissen.playback

import org.grakovne.lissen.content.LissenMediaProvider
import javax.inject.Inject
import javax.inject.Singleton

fun interface ServerReachability {
  suspend fun isReachable(): Boolean
}

/** Asks the server over the route playback would use: a registered local URL on its Wi-Fi network, the external one elsewhere. */
@Singleton
class MediaProviderServerReachability
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
  ) : ServerReachability {
    override suspend fun isReachable(): Boolean = mediaProvider.fetchConnectionInfo().fold(onSuccess = { true }, onFailure = { false })
  }
