package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.RunningComponent
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
    private val sharedPreferences: LissenSharedPreferences,
    private val networkQualityService: NetworkQualityService,
  ) : RunningComponent {
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
        ) { playingItem: DetailedItem?, isPlaying: Boolean, _: Int? -> playingItem to isPlaying }
          .collectLatest { (playingItem, isPlaying) -> updatePlaybackCache(playingItem, isPlaying) }
      }
    }

    private fun updatePlaybackCache(
      playingItem: DetailedItem?,
      isPlaying: Boolean,
    ) {
      val playbackCacheOption = sharedPreferences.getAutoDownloadOption() ?: return
      val isNetworkAvailable = networkQualityService.isNetworkAvailable()
      val currentNetwork = networkQualityService.getCurrentNetworkType() ?: return

      val playingMediaItem = playingItem ?: return
      val preferredNetwork = sharedPreferences.getAutoDownloadNetworkType()
      val currentTotalPosition = mediaRepository.totalPosition.value ?: return

      val isForceCache = sharedPreferences.isForceCache()

      val cacheAvailable = isNetworkAvailable && isPlaying && isForceCache.not() && validNetworkType(currentNetwork, preferredNetwork)

      if (cacheAvailable.not()) {
        return
      }

      val task =
        ContentCachingTask(
          item = playingMediaItem,
          options = playbackCacheOption,
          currentPosition = currentTotalPosition,
        )

      val intent =
        Intent(context, ContentCachingService::class.java).apply {
          putExtra(ContentCachingService.CACHING_TASK_EXTRA, task as Serializable)
        }

      context.startForegroundService(intent)
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
      private const val TAG = "ContentAutoCachingService"
    }
  }
