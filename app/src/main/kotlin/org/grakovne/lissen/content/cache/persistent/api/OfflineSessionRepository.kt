package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.converter.OfflineSessionEntityConverter
import org.grakovne.lissen.content.cache.persistent.converter.toEntity
import org.grakovne.lissen.content.cache.persistent.dao.OfflineSessionDao
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.OfflineSession
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.accumulateOfflineSession
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSessionRepository
  @Inject
  constructor(
    private val dao: OfflineSessionDao,
    private val converter: OfflineSessionEntityConverter,
  ) {
    /**
     * A snapshot with nothing listened does not open a row: a paused player syncs too,
     * and an empty session is noise in the server's listening history.
     */
    suspend fun record(
      sessionId: String,
      item: DetailedItem,
      chapterIndex: Int,
      progress: PlaybackProgress,
      timeListened: Double,
    ): OfflineSession? {
      val existing = dao.fetchById(sessionId)?.let { converter.apply(it) }

      if (existing == null && timeListened <= 0.0) {
        Timber.d("Nothing listened in offline session $sessionId for ${item.id} yet, not recording")
        return null
      }

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

      dao.upsert(session.toEntity())
      return session
    }

    suspend fun fetch(): List<OfflineSession> = dao.fetchAll().map(converter::apply)

    suspend fun drop(ids: List<String>) {
      if (ids.isEmpty()) return
      dao.deleteByIds(ids)
    }

    /** The rows belong to one login: both a logout and a login drop them. */
    suspend fun dropAll() {
      val dropped = dao.deleteAll()
      Timber.d("Dropped $dropped offline session(s)")
    }
  }
