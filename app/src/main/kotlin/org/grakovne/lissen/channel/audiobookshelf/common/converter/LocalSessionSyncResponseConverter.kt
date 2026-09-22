package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionSyncResponse
import org.grakovne.lissen.domain.OfflineSessionSyncResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSessionSyncResponseConverter
  @Inject
  constructor() {
    fun apply(response: LocalSessionSyncResponse): List<OfflineSessionSyncResult> =
      response.results.map {
        OfflineSessionSyncResult(
          id = it.id,
          success = it.success,
          error = it.error,
        )
      }
  }
