package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflineSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSessionEntityConverter
  @Inject
  constructor() {
    fun apply(entity: OfflineSessionEntity): OfflineSession =
      OfflineSession(
        id = entity.id,
        libraryItemId = entity.libraryItemId,
        episodeId = entity.episodeId,
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
  }

internal fun OfflineSession.toEntity(): OfflineSessionEntity =
  OfflineSessionEntity(
    id = id,
    libraryItemId = libraryItemId,
    episodeId = episodeId,
    libraryType = libraryType.name,
    displayTitle = displayTitle,
    displayAuthor = displayAuthor,
    duration = duration,
    startTime = startTime,
    currentTime = currentTime,
    timeListening = timeListening,
    startedAt = startedAt,
    updatedAt = updatedAt,
  )
