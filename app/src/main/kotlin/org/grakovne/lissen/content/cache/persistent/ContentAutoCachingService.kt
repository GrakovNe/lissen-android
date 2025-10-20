package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.ContentCachingTask
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.NetworkType
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import java.io.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class ContentAutoCachingService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
    private val sharedPreferences: LissenSharedPreferences,
    private val networkService: NetworkService,
  ) : RunningComponent {
    private var delayedJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
      scope.launch {
        combine(
          mediaRepository.playingBook.asFlow().distinctUntilChanged(),
          mediaRepository.isPlaying
            .asFlow()
            .filterNotNull()
            .distinctUntilChanged(),
          mediaRepository.currentChapterIndex.asFlow().distinctUntilChanged(),
        ) { playingItem: DetailedItem?, isPlaying: Boolean, _: Int? ->
          playingItem to isPlaying
        }.collectLatest { (playingItem, isPlaying) ->
          delayedJob?.cancel()
          delayedJob = updatePlaybackCache(playingItem, isPlaying)
        }
      }
    }

    private suspend fun updatePlaybackCache(
      playingItem: DetailedItem?,
      isPlaying: Boolean,
      delayed: Boolean = false,
    ): Job? {
      val playbackCacheOption = sharedPreferences.getAutoDownloadOption() ?: return null
      val playingMediaItem = playingItem ?: return null

      val isNetworkAvailable = networkService.isNetworkAvailable()
      val currentNetwork = networkService.getCurrentNetworkType() ?: return null
      val preferredNetwork = sharedPreferences.getAutoDownloadNetworkType()
      val currentTotalPosition = mediaRepository.totalPosition.value ?: return null

      val playingItemLibraryType =
        mediaProvider
          .providePreferredChannel()
          .fetchLibraries()
          .fold(
            onSuccess = { libraries -> libraries.find { it.id == playingMediaItem.libraryId }?.type },
            onFailure = { null },
          ) ?: return null

      val requestedLibraryType =
        sharedPreferences
          .getAutoDownloadLibraryTypes()
          .contains(playingItemLibraryType)

      val isForceCache = sharedPreferences.isForceCache()

      val cacheAvailable =
        isNetworkAvailable &&
          isPlaying &&
          isForceCache.not() &&
          validNetworkType(currentNetwork, preferredNetwork) &&
          requestedLibraryType

      if (cacheAvailable.not()) return null

      if (sharedPreferences.getAutoDownloadDelayed().not() || delayed) {
        val task =
          ContentCachingTask(
            item = playingMediaItem,
            options = playbackCacheOption,
            currentPosition = currentTotalPosition,
          )

        val intent =
          Intent(context, ContentCachingService::class.java).apply {
            action = ContentCachingService.CACHE_ITEM_ACTION
            putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
          }

        context.startForegroundService(intent)
        return null
      }

      return scope.launch {
        val originalBookId = playingMediaItem.id
        delay(DELAY_TIME)

        val currentPlaying = mediaRepository.playingBook.value
        if (currentPlaying?.id != originalBookId) return@launch

        updatePlaybackCache(currentPlaying, isPlaying, delayed = true)
      }
    }

    private fun validNetworkType(
      current: NetworkType,
      required: NetworkTypeAutoCache,
    ): Boolean {
      val positiveNetworkTypes =
        when (required) {
          NetworkTypeAutoCache.WIFI_ONLY -> listOf(NetworkType.WIFI)
          NetworkTypeAutoCache.WIFI_OR_CELLULAR -> listOf(NetworkType.WIFI, NetworkType.CELLULAR)
        }

      return positiveNetworkTypes.contains(current)
    }

    companion object {
      private const val DELAY_TIME: Long = 30_000
    }
  }
