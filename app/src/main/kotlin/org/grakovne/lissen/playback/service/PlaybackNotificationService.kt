package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackNotificationService
  @Inject
  constructor(
    private val lissenPlayer: LissenPlayer,
    private val sharedPreferences: LissenSharedPreferences,
  ) : RunningComponent {
    override fun onCreate() {
      lissenPlayer.addListener(
        object : Player.Listener {
          override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
          ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (playWhenReady) {
              lissenPlayer.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
            }
          }

          override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
          ) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
              return
            }

            lissenPlayer.onMediaItemTransition()
          }
        },
      )
    }
  }
