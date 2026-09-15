package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.DeviceInfo
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.playback.LocalSessionSyncResponse
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.domain.OfflineSessionSyncResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSessionRequestConverter
  @Inject
  constructor() {
    fun apply(
      session: OfflinePlaybackSession,
      deviceInfo: DeviceInfo,
      mediaPlayer: String,
    ): LocalSessionRequest {
      val updatedAt = Instant.ofEpochMilli(session.updatedAt).atZone(ZoneId.systemDefault())

      return LocalSessionRequest(
        id = session.id,
        libraryItemId = session.libraryItemId,
        episodeId = session.episodeId,
        mediaType =
          when (session.libraryType) {
            LibraryType.PODCAST -> MEDIA_TYPE_PODCAST
            LibraryType.LIBRARY, LibraryType.UNKNOWN -> MEDIA_TYPE_BOOK
          },
        displayTitle = session.displayTitle,
        displayAuthor = session.displayAuthor,
        duration = session.duration,
        playMethod = PLAY_METHOD_LOCAL,
        mediaPlayer = mediaPlayer,
        deviceInfo = deviceInfo,
        date = updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE),
        dayOfWeek = updatedAt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
        startTime = session.startTime,
        currentTime = session.currentTime,
        timeListening = session.timeListening,
        startedAt = session.startedAt,
        updatedAt = session.updatedAt,
      )
    }

    fun apply(response: LocalSessionSyncResponse): List<OfflineSessionSyncResult> =
      response.results.map {
        OfflineSessionSyncResult(
          id = it.id,
          success = it.success,
          error = it.error,
        )
      }

    companion object {
      private const val MEDIA_TYPE_BOOK = "book"
      private const val MEDIA_TYPE_PODCAST = "podcast"

      // PlayMethod.LOCAL in the Audiobookshelf server: media played from a device-side copy.
      private const val PLAY_METHOD_LOCAL = 3
    }
  }
