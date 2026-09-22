package org.grakovne.lissen.domain

import androidx.annotation.Keep

/**
 * A listening session recorded while the server was unreachable. Rows are
 * accumulated locally by the playback synchronization and uploaded in a batch
 * once connectivity returns, so the server gets both the position and the
 * listening statistics for the offline period. Rows belong to the account
 * that is logged in while they are recorded: a login or a logout drops them.
 */
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
