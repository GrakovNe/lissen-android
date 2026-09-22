package org.grakovne.lissen.domain

/**
 * Folds one sync snapshot into the offline session identified by [sessionId].
 * The first snapshot with listened time creates the row and pins its start
 * position; every later one advances the position and adds the listened time.
 * Podcast episodes are reported against the episode (the playing chapter)
 * rather than the whole feed, matching what the online sync sends. Nothing is
 * created for a snapshot with nothing listened: a paused player syncs too, and
 * an empty session is noise in the server's listening history.
 */
fun accumulateOfflineSession(
  existing: OfflineSession?,
  sessionId: String,
  item: DetailedItem,
  libraryType: LibraryType,
  chapterIndex: Int,
  progress: PlaybackProgress,
  timeListened: Double,
  now: Long,
): OfflineSession? {
  val episode =
    item.chapters
      .getOrNull(chapterIndex)
      ?.takeIf { libraryType == LibraryType.PODCAST }

  val currentTime =
    when (episode) {
      null -> progress.currentTotalTime
      else -> progress.currentChapterTime
    }

  return when {
    existing != null -> {
      existing.copy(
        currentTime = currentTime,
        timeListening = existing.timeListening + timeListened,
        updatedAt = now,
      )
    }

    timeListened <= 0.0 -> {
      null
    }

    else -> {
      OfflineSession(
        id = sessionId,
        libraryItemId = item.id,
        episodeId = episode?.id,
        libraryType = libraryType,
        displayTitle = episode?.title ?: item.title,
        displayAuthor = item.author,
        duration = episode?.duration ?: item.chapters.sumOf { it.duration },
        startTime = currentTime,
        currentTime = currentTime,
        timeListening = timeListened,
        startedAt = now,
        updatedAt = now,
      )
    }
  }
}
