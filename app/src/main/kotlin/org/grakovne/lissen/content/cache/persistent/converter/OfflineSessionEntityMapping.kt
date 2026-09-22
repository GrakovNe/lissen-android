package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity
import org.grakovne.lissen.domain.OfflineSession

internal fun OfflineSessionEntity.toDomain(): OfflineSession =
  OfflineSession(
    id = id,
    libraryItemId = libraryItemId,
    episodeId = episodeId,
    libraryType = libraryType,
    displayTitle = displayTitle,
    displayAuthor = displayAuthor,
    duration = duration,
    startTime = startTime,
    currentTime = currentTime,
    timeListening = timeListening,
    startedAt = startedAt,
    updatedAt = updatedAt,
  )

internal fun OfflineSession.toEntity(): OfflineSessionEntity =
  OfflineSessionEntity(
    id = id,
    libraryItemId = libraryItemId,
    episodeId = episodeId,
    libraryType = libraryType,
    displayTitle = displayTitle,
    displayAuthor = displayAuthor,
    duration = duration,
    startTime = startTime,
    currentTime = currentTime,
    timeListening = timeListening,
    startedAt = startedAt,
    updatedAt = updatedAt,
  )
