package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.ListeningRecordEntity
import org.grakovne.lissen.domain.ListeningMediaType
import org.grakovne.lissen.domain.ListeningRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningRecordEntityConverter
  @Inject
  constructor() {
    fun apply(entity: ListeningRecordEntity): ListeningRecord =
      ListeningRecord(
        id = entity.id,
        itemId = entity.itemId,
        episodeId = entity.episodeId,
        mediaType = ListeningMediaType.valueOf(entity.mediaType),
        displayTitle = entity.displayTitle,
        duration = entity.duration,
        startTime = entity.startTime,
        currentTime = entity.currentTime,
        timeListeningMs = entity.timeListeningMs,
        startedAt = entity.startedAt,
        updatedAt = entity.updatedAt,
      )

    fun apply(
      record: ListeningRecord,
      account: String,
      synced: Boolean,
    ): ListeningRecordEntity =
      ListeningRecordEntity(
        id = record.id,
        itemId = record.itemId,
        episodeId = record.episodeId,
        mediaType = record.mediaType.name,
        displayTitle = record.displayTitle,
        duration = record.duration,
        startTime = record.startTime,
        currentTime = record.currentTime,
        timeListeningMs = record.timeListeningMs,
        startedAt = record.startedAt,
        updatedAt = record.updatedAt,
        account = account,
        synced = synced,
      )
  }
