package org.grakovne.lissen.domain

/**
 * Folds one sync snapshot into the offline session identified by [sessionId].
 * The first snapshot creates the row and pins its start position; every later
 * one advances the position and adds the listened time. Podcast episodes are
 * reported against the episode (the playing chapter) rather than the whole feed,
 * matching what the online sync sends.
 */
fun accumulateOfflineSession(
  existing: OfflineSession?,
  sessionId: String,
  owner: OfflineSessionOwner,
  item: DetailedItem,
  chapterIndex: Int,
  progress: PlaybackProgress,
  timeListened: Double,
  now: Long,
): OfflineSession {
  val scope = sessionScope(item, chapterIndex, progress)

  return existing
    ?.copy(
      currentTime = scope.currentTime,
      timeListening = existing.timeListening + timeListened,
      updatedAt = now,
    )
    ?: OfflineSession(
      id = sessionId,
      owner = owner,
      libraryItemId = item.id,
      episodeId = scope.episodeId,
      libraryId = item.libraryId,
      libraryType = item.libraryType ?: LibraryType.LIBRARY,
      displayTitle = scope.title,
      displayAuthor = item.author,
      duration = scope.duration,
      startTime = scope.currentTime,
      currentTime = scope.currentTime,
      timeListening = timeListened,
      startedAt = now,
      updatedAt = now,
    )
}

/** What the session is reported against: a single podcast episode or the whole item. */
private data class SessionScope(
  val episodeId: String?,
  val title: String,
  val duration: Double,
  val currentTime: Double,
)

private fun sessionScope(
  item: DetailedItem,
  chapterIndex: Int,
  progress: PlaybackProgress,
): SessionScope {
  val episode =
    item.chapters
      .getOrNull(chapterIndex)
      ?.takeIf { item.libraryType == LibraryType.PODCAST }

  return when (episode) {
    null -> {
      SessionScope(
        episodeId = null,
        title = item.title,
        duration = item.chapters.sumOf { it.duration },
        currentTime = progress.currentTotalTime,
      )
    }

    else -> {
      SessionScope(
        episodeId = episode.id,
        title = episode.title,
        duration = episode.duration,
        currentTime = progress.currentChapterTime,
      )
    }
  }
}
