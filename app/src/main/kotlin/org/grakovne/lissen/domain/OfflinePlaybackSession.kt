package org.grakovne.lissen.domain

import androidx.annotation.Keep

/**
 * A listening session recorded while the server was unreachable. Rows are
 * accumulated locally by the playback synchronization and uploaded in a batch
 * once connectivity returns, so the server gets both the position and the
 * listening statistics for the offline period.
 */
@Keep
data class OfflinePlaybackSession(
  val id: String,
  val libraryItemId: String,
  val episodeId: String?,
  val libraryId: String?,
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
