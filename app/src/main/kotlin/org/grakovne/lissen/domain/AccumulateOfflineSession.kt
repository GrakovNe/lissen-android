package org.grakovne.lissen.domain

/**
 * Folds a sync snapshot into session [sessionId]: the first listened snapshot creates the row,
 * later ones advance it. Podcast episodes are reported per episode, as the online sync does.
 * Nothing listened, nothing recorded.
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
