package org.grakovne.lissen.content

import org.grakovne.lissen.content.cache.persistent.converter.ListeningRecordEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.ListeningRecordDao
import org.grakovne.lissen.domain.ListeningRecord
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningRecordRepository
  @Inject
  constructor(
    private val listeningRecordDao: ListeningRecordDao,
    private val converter: ListeningRecordEntityConverter,
    private val sessionPreferences: SessionPreferences,
  ) {
    suspend fun upsert(record: ListeningRecord) =
      listeningRecordDao.upsert(
        converter.apply(
          record = record,
          account = provideAccountHash(),
          synced = false,
        ),
      )

    suspend fun markSynced(records: List<ListeningRecord>) = records.forEach { listeningRecordDao.markSynced(it.id, it.updatedAt) }

    suspend fun fetchUnsynced(now: Long): List<ListeningRecord> =
      listeningRecordDao
        .fetchUnsyncedByAccount(
          account = provideAccountHash(),
          threshold = now - BACKLOG_MIN_AGE_MS,
        ).map { converter.apply(it) }

    suspend fun dropStale(now: Long) {
      listeningRecordDao.deleteSyncedOlderThan(now - SYNCED_RETENTION_MS)
      listeningRecordDao.deleteUnsyncedOlderThan(now - UNSYNCED_RETENTION_MS)
    }

    suspend fun dropAll() = listeningRecordDao.deleteAll()

    private fun provideAccountHash(): String =
      calculateAccountHash(
        host = sessionPreferences.getHost(),
        username = sessionPreferences.getUsername(),
      )
  }

internal const val BACKLOG_MIN_AGE_MS = 90_000L
internal const val SYNCED_RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
internal const val UNSYNCED_RETENTION_MS = 30 * 24 * 60 * 60 * 1000L
