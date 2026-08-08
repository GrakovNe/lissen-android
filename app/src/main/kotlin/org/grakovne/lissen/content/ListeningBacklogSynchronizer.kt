package org.grakovne.lissen.content

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.CoalescingRunner
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
    private val runner = CoalescingRunner<Unit>()

    override fun onCreate() {
      requestSynchronization()
    }

    fun requestSynchronization() {
      scope.launch { runner.submit(Unit) { synchronize() } }
    }

    private suspend fun synchronize() {
      listeningRecordRepository.dropStale(System.currentTimeMillis())

      if (preferences.isForceCache()) return
      if (networkService.isNetworkAvailable().not()) return

      val records = listeningRecordRepository.fetchUnsynced(System.currentTimeMillis())

      if (records.isNotEmpty()) {
        Timber.d("Syncing listening backlog: records=${records.size}")
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
    }
  }

internal const val BACKLOG_CHUNK_SIZE = 50
