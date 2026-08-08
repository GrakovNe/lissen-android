package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionRequest
import org.grakovne.lissen.domain.ListeningMediaType
import org.grakovne.lissen.domain.ListeningRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningRecordRequestConverter
  @Inject
  constructor() {
    fun apply(
      record: ListeningRecord,
      deviceId: String,
    ): LocalSessionRequest {
      val clientName = "Lissen App ${BuildConfig.VERSION_NAME}"

      return LocalSessionRequest(
        id = record.id,
        libraryItemId = record.itemId,
        episodeId = record.episodeId,
        mediaType =
          when (record.mediaType) {
            ListeningMediaType.PODCAST -> "podcast"
            ListeningMediaType.BOOK -> "book"
          },
        displayTitle = record.displayTitle,
        duration = record.duration,
        playMethod = LOCAL_PLAY_METHOD,
        mediaPlayer = clientName,
        deviceInfo =
          DeviceInfo(
            clientName = clientName,
            deviceId = deviceId,
            deviceName = clientName,
          ),
        timeListening = record.timeListeningMs / 1000.0,
        startTime = record.startTime,
        currentTime = record.currentTime,
        startedAt = record.startedAt,
        updatedAt = record.updatedAt,
      )
    }
  }

internal const val LOCAL_PLAY_METHOD = 3
