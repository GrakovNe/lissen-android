package org.grakovne.lissen.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningBacklogSynchronizer
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val listeningRecordRepository: ListeningRecordRepository,
    private val preferences: LibraryPreferences,
    private val networkService: NetworkService,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
      requestSynchronization()
    }

    fun requestSynchronization() {
      scope.launch { synchronize() }
    }

    private suspend fun synchronize() {
      if (preferences.isForceCache()) return
      if (networkService.isNetworkAvailable().not()) return

      val records = listeningRecordRepository.fetchUnsynced()

      if (records.isNotEmpty()) {
        Timber.d("Syncing listening backlog of ${records.size} records")
      }

      records
        .chunked(BACKLOG_CHUNK_SIZE)
        .forEach { chunk ->
          mediaProvider
            .providePreferredChannel()
            .syncListeningBacklog(chunk)
            .foldAsync(
              onSuccess = { synced ->
                if (synced.isNotEmpty()) {
                  listeningRecordRepository.markSynced(synced)
                }
              },
              onFailure = {},
            )
        }

      listeningRecordRepository.dropStale(System.currentTimeMillis())
    }
  }

internal const val BACKLOG_CHUNK_SIZE = 50
