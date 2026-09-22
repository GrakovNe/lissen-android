package org.grakovne.lissen.domain

import androidx.annotation.Keep
import java.net.URI

/**
 * A listening session recorded while the server was unreachable. Rows are
 * accumulated locally by the playback synchronization and uploaded in a batch
 * once connectivity returns, so the server gets both the position and the
 * listening statistics for the offline period.
 */
@Keep
data class OfflineSession(
  val id: String,
  val owner: OfflineSessionOwner,
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
data class OfflineSessionOwner(
  val serverHost: String,
  val username: String,
) {
  companion object {
    fun from(
      serverHost: String?,
      username: String?,
    ): OfflineSessionOwner? {
      val normalizedHost = serverHost?.normalizeServerHost() ?: return null
      val normalizedUsername = username?.trim()?.takeIf(String::isNotEmpty) ?: return null

      return OfflineSessionOwner(
        serverHost = normalizedHost,
        username = normalizedUsername,
      )
    }
  }
}

private fun String.normalizeServerHost(): String? {
  val source = trim().trimEnd('/').takeIf(String::isNotEmpty) ?: return null

  return runCatching {
    val uri = URI(source)
    URI(
      uri.scheme?.lowercase(),
      uri.userInfo,
      uri.host?.lowercase(),
      uri.port,
      uri.path?.trimEnd('/').orEmpty(),
      uri.query,
      uri.fragment,
    ).toString()
  }.getOrDefault(source)
}

@Keep
data class OfflineSessionSyncResult(
  val id: String,
  val success: Boolean,
  val error: String?,
)
