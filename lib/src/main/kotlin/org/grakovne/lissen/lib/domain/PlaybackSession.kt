package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import java.util.UUID

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
        fun local(itemId: String): PlaybackSession {
            return PlaybackSession(
                sessionId = "local-${UUID.randomUUID()}",
                itemId = itemId,
                sessionSource = PlaybackSessionSource.LOCAL,
            )
        }

        fun remote(sessionId: String, itemId: String): PlaybackSession {
            return PlaybackSession(
                sessionId = sessionId,
                itemId = itemId,
                sessionSource = PlaybackSessionSource.REMOTE,
            )
        }
    }
}
