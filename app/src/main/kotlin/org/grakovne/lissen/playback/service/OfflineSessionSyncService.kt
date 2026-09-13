package org.grakovne.lissen.playback.service

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.OfflinePlaybackSession
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replays sessions recorded while offline to the server. Runs when the default
 * network comes back and when the playback synchronization manages to open a
 * remote session again; the session currently being written by the
 * synchronization is skipped so a half-recorded row is never uploaded and then
 * recreated from zero.
 */
@Singleton
class OfflineSessionSyncService
  @Inject
  constructor(
    private val mediaProvider: LissenMediaProvider,
    private val networkService: NetworkService,
    private val sessionPreferences: SessionPreferences,
    private val libraryPreferences: LibraryPreferences,
  ) : RunningComponent {
    @Volatile
    var activeSessionId: String? = null

    private val uploadMutex = Mutex()
    private val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
          CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Offline session upload failed, ignoring")
          },
      )

    override fun onCreate() {
      scope.launch {
        networkService
          .networkAvailable
          .filter { it }
          .collect { upload() }
      }
    }

    fun requestUpload() {
      scope.launch { upload() }
    }

    suspend fun upload() {
      if (libraryPreferences.isForceCache()) {
        Timber.d("Skipping offline session upload: offline mode is forced")
        return
      }

      if (networkService.isNetworkAvailable().not()) {
        Timber.d("Skipping offline session upload: network is unavailable")
        return
      }

      uploadMutex.withLock {
        val pending =
          mediaProvider
            .fetchOfflineSessions()
            .filter { it.id != activeSessionId }

        if (pending.isEmpty()) return

        Timber.d("Uploading ${pending.size} pending offline session(s)")

        pending
          .groupBy { it.libraryType }
          .forEach { (libraryType, sessions) -> uploadBatch(libraryType, sessions) }
      }
    }

    private suspend fun uploadBatch(
      libraryType: LibraryType,
      sessions: List<OfflinePlaybackSession>,
    ) {
      mediaProvider
        .syncOfflineSessions(
          libraryType = libraryType,
          sessions = sessions,
          deviceId = sessionPreferences.getDeviceId(),
        ).foldAsync(
          onSuccess = { results ->
            results
              .filterNot { it.success }
              .forEach { Timber.w("Server rejected offline session ${it.id}: ${it.error}") }

            // A rejected session (item no longer on the server, missing episode) will
            // never be accepted, so it is dropped along with the accepted ones.
            mediaProvider.dropOfflineSessions(results.map { it.id })
          },
          onFailure = { Timber.w("Unable to upload offline sessions for $libraryType: ${it.code}") },
        )
    }
  }
