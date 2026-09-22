package org.grakovne.lissen.channel.audiobookshelf.common.model.playback

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

/** Reply to `POST /api/session/local-all`: one verdict per uploaded session, keyed by its id. */
@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncResponse(
  val results: List<LocalSessionSyncResultResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LocalSessionSyncResultResponse(
  val id: String,
  val success: Boolean,
  val error: String? = null,
)
