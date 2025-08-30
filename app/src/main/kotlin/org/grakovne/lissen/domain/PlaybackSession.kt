package org.grakovne.lissen.domain

import androidx.annotation.Keep

@Keep
data class PlaybackSession(
  val sessionId: String,
  val itemId: String,
)
