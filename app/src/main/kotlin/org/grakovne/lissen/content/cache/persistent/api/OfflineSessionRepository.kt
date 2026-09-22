package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.converter.toDomain
import org.grakovne.lissen.content.cache.persistent.converter.toEntity
import org.grakovne.lissen.content.cache.persistent.dao.OfflineSessionDao
import org.grakovne.lissen.content.cache.persistent.entity.OfflineSessionEntity
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.LibraryType
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
  ) {
    suspend fun record(
      sessionId: String,
      item: DetailedItem,
      libraryType: LibraryType,
      chapterIndex: Int,
      progress: PlaybackProgress,
      timeListened: Double,
    ) {
      val session =
        accumulateOfflineSession(
          existing = dao.fetchById(sessionId)?.toDomain(),
          sessionId = sessionId,
          item = item,
          libraryType = libraryType,
          chapterIndex = chapterIndex,
          progress = progress,
          timeListened = timeListened,
          now = Instant.now().toEpochMilli(),
        ) ?: return

      Timber.d(
        "Recording offline session $sessionId for ${item.id}: position=${session.currentTime.toInt()}s, listened=${session.timeListening.toInt()}s",
      )
      dao.upsert(session.toEntity())
    }

    suspend fun fetch(): List<OfflineSession> = dao.fetchAll().map(OfflineSessionEntity::toDomain)

    suspend fun drop(ids: Collection<String>) {
      if (ids.isEmpty()) return
      dao.deleteByIds(ids.toList())
    }

    /** The rows belong to one login: both a logout and a login drop them. */
    suspend fun dropAll() {
      val dropped = dao.deleteAll()
      Timber.d("Dropped $dropped offline session(s)")
    }
  }
