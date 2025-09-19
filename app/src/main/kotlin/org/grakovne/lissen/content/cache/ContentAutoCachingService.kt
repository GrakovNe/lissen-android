package org.grakovne.lissen.content.cache

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.common.NetworkTypeAutoCache
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.lib.domain.ContentCachingTask
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
    private val exoPlayer: ExoPlayer,
    private val sharedPreferences: LissenSharedPreferences,
    private val networkQualityService: NetworkQualityService,
  ) : RunningComponent {
    override fun onCreate() {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onEvents(
            player: Player,
            events: Player.Events,
          ) {
            if (cacheEvents.any(events::contains)) {
              updatePlaybackCache()
            }
          }
        },
      )
    }

    private fun updatePlaybackCache() {
      val playbackCacheOption = sharedPreferences.getAutoDownloadOption() ?: return
      val currentNetworkType = networkQualityService.getCurrentNetworkType() ?: return

      val playingMediaItem = mediaRepository.playingBook.value ?: return
      val playbackCacheNetworkType = sharedPreferences.getAutoDownloadNetworkType()
      val currentTotalPosition = mediaRepository.totalPosition.value ?: return

      val isForceCache = sharedPreferences.isForceCache()

      if (isForceCache || validNetworkType(currentNetworkType, playbackCacheNetworkType).not()) {
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

      private val cacheEvents =
        listOf(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          Player.EVENT_IS_PLAYING_CHANGED,
        )
    }
  }
