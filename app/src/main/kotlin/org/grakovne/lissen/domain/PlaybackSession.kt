package org.grakovne.lissen.domain

import androidx.annotation.Keep
import java.util.UUID

@Keep
enum class PlaybackSessionSource {
  LOCAL,
  REMOTE,
}

@Keep
data class PlaybackSession(
  val sessionId: String,
  val itemId: String,
  val sessionSource: PlaybackSessionSource,
) {
  companion object {
    fun local(itemId: String): PlaybackSession =
      PlaybackSession(
        // A plain UUID, so the server stores an uploaded offline session under this very id.
        sessionId = UUID.randomUUID().toString(),
        itemId = itemId,
        sessionSource = PlaybackSessionSource.LOCAL,
      )

    fun remote(
      sessionId: String,
      itemId: String,
    ): PlaybackSession =
      PlaybackSession(
        sessionId = sessionId,
        itemId = itemId,
        sessionSource = PlaybackSessionSource.REMOTE,
      )
  }
}
