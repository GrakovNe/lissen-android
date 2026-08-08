package org.grakovne.lissen.content

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.ListeningMediaType
import org.grakovne.lissen.domain.ListeningRecord
import org.grakovne.lissen.domain.ListeningSession

fun buildListeningRecord(
  item: DetailedItem,
  session: ListeningSession,
  type: LibraryType,
): ListeningRecord =
  when (type) {
    LibraryType.PODCAST -> {
      ListeningRecord(
        id = session.id,
        itemId = session.itemId,
        episodeId = session.chapterId,
        mediaType = ListeningMediaType.PODCAST,
        displayTitle = item.title,
        duration = item.chapters.find { it.id == session.chapterId }?.duration ?: 0.0,
        startTime = 0.0,
        currentTime = session.progress.currentChapterTime,
        timeListeningMs = session.timeListeningMs,
        startedAt = session.startedAt,
        updatedAt = session.updatedAt,
      )
    }

    LibraryType.LIBRARY, LibraryType.UNKNOWN -> {
      ListeningRecord(
        id = session.id,
        itemId = session.itemId,
        episodeId = null,
        mediaType = ListeningMediaType.BOOK,
        displayTitle = item.title,
        duration = item.chapters.sumOf { it.duration },
        startTime = session.startTime,
        currentTime = session.progress.currentTotalTime,
        timeListeningMs = session.timeListeningMs,
        startedAt = session.startedAt,
        updatedAt = session.updatedAt,
      )
    }
  }
