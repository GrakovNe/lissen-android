package org.grakovne.lissen.domain

/**
 * Folds one sync snapshot into the offline session identified by [sessionId].
 * The first snapshot creates the row and pins its start position; every later
 * one advances the position and adds the listened time. Podcast episodes are
 * reported against the episode (the playing chapter) rather than the whole feed,
 * matching what the online sync sends.
 */
fun accumulateOfflineSession(
  existing: OfflinePlaybackSession?,
  sessionId: String,
  owner: OfflineSessionOwner,
  item: DetailedItem,
  chapterIndex: Int,
  progress: PlaybackProgress,
  timeListened: Double,
  now: Long,
): OfflinePlaybackSession {
  val chapter = item.chapters.getOrNull(chapterIndex)
  val isPodcast = item.libraryType == LibraryType.PODCAST

  val currentTime =
    when (isPodcast) {
      true -> progress.currentChapterTime
      false -> progress.currentTotalTime
    }

  val duration =
    when (isPodcast) {
      true -> chapter?.duration ?: item.chapters.sumOf { it.duration }
      false -> item.chapters.sumOf { it.duration }
    }

  return existing
    ?.copy(
      currentTime = currentTime,
      timeListening = existing.timeListening + timeListened,
      updatedAt = now,
    )
    ?: OfflinePlaybackSession(
      id = sessionId,
      owner = owner,
      libraryItemId = item.id,
      episodeId = chapter?.id?.takeIf { isPodcast },
      libraryId = item.libraryId,
      libraryType = item.libraryType ?: LibraryType.LIBRARY,
      displayTitle = chapter?.title?.takeIf { isPodcast } ?: item.title,
      displayAuthor = item.author,
      duration = duration,
      startTime = currentTime,
      currentTime = currentTime,
      timeListening = timeListened,
      startedAt = now,
      updatedAt = now,
    )
}
