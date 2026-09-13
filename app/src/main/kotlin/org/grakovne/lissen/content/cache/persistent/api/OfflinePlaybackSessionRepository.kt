package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.converter.OfflinePlaybackSessionEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.OfflinePlaybackSessionDao
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.accumulateOfflineSession
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePlaybackSessionRepository
  @Inject
  constructor(
    private val dao: OfflinePlaybackSessionDao,
    private val converter: OfflinePlaybackSessionEntityConverter,
  ) {
    suspend fun record(
      sessionId: String,
      item: DetailedItem,
      chapterIndex: Int,
      progress: PlaybackProgress,
      timeListened: Double,
    ): OfflinePlaybackSession {
      val existing = dao.fetchById(sessionId)?.let { converter.apply(it) }

      val session =
        accumulateOfflineSession(
          existing = existing,
          sessionId = sessionId,
          item = item,
          chapterIndex = chapterIndex,
          progress = progress,
          timeListened = timeListened,
          now = Instant.now().toEpochMilli(),
        )

      Timber.d(
        "Recording offline session $sessionId for ${item.id}: position=${session.currentTime.toInt()}s, listened=${session.timeListening.toInt()}s",
      )

      dao.upsert(converter.apply(session))
      return session
    }

    suspend fun fetchAll(): List<OfflinePlaybackSession> = dao.fetchAll().map { converter.apply(it) }

    suspend fun drop(ids: List<String>) {
      if (ids.isEmpty()) return
      dao.deleteByIds(ids)
    }
  }
