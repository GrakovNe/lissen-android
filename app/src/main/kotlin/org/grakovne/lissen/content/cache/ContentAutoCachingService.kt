package org.grakovne.lissen.content.cache

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class ContentAutoCachingService
  (
  private val cacheManager: ContentCachingManager,
  private val mediaRepository: MediaRepository,
  private val exoPlayer: ExoPlayer,
  private val sharedPreferences: LissenSharedPreferences
) : RunningComponent {
  
  override fun onCreate() {
    exoPlayer.addListener(object : Player.Listener {
      override fun onEvents(player: Player, events: Player.Events) {
        if (cacheEvents.any(events::contains)) {
          updatePlaybackCache()
        }
      }
    })
  }
  
  private fun updatePlaybackCache() {
    val playbackCacheEnabled = sharedPreferences.getAutoDownloadOption()
  }
  
  companion object {
    private val cacheEvents =
      listOf(
        Player.EVENT_MEDIA_ITEM_TRANSITION,
        Player.EVENT_PLAYBACK_STATE_CHANGED,
        Player.EVENT_IS_PLAYING_CHANGED,
      )
  }
  
  
}
