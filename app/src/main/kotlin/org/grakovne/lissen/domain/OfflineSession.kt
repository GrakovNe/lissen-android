package org.grakovne.lissen.domain

import androidx.annotation.Keep

/** Recorded while the server was unreachable, uploaded in a batch once it is back. Rows belong to the account logged in at the time. */
@Keep
data class OfflineSession(
  val id: String,
  val libraryItemId: String,
  val episodeId: String?,
  val libraryType: LibraryType,
  val displayTitle: String,
  val displayAuthor: String?,
  val duration: Double,
  val startTime: Double,
  val currentTime: Double,
  val timeListening: Double,
  val startedAt: Long,
  val updatedAt: Long,
)

@Keep
data class OfflineSessionSyncResult(
  val id: String,
  val success: Boolean,
  val error: String?,
)
