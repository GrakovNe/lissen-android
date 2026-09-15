package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.OfflinePlaybackSessionEntity
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePlaybackSessionEntityConverter
  @Inject
  constructor() {
    fun apply(entity: OfflinePlaybackSessionEntity): OfflinePlaybackSession =
      OfflinePlaybackSession(
        id = entity.id,
        libraryItemId = entity.libraryItemId,
        episodeId = entity.episodeId,
        libraryId = entity.libraryId,
        libraryType = runCatching { LibraryType.valueOf(entity.libraryType) }.getOrDefault(LibraryType.UNKNOWN),
        displayTitle = entity.displayTitle,
        displayAuthor = entity.displayAuthor,
        duration = entity.duration,
        startTime = entity.startTime,
        currentTime = entity.currentTime,
        timeListening = entity.timeListening,
        startedAt = entity.startedAt,
        updatedAt = entity.updatedAt,
      )

    fun apply(session: OfflinePlaybackSession): OfflinePlaybackSessionEntity =
      OfflinePlaybackSessionEntity(
        id = session.id,
        libraryItemId = session.libraryItemId,
        episodeId = session.episodeId,
        libraryId = session.libraryId,
        libraryType = session.libraryType.name,
        displayTitle = session.displayTitle,
        displayAuthor = session.displayAuthor,
        duration = session.duration,
        startTime = session.startTime,
        currentTime = session.currentTime,
        timeListening = session.timeListening,
        startedAt = session.startedAt,
        updatedAt = session.updatedAt,
      )
  }
