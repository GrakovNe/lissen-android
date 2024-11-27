package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackNotificationService @Inject constructor(
    private val exoPlayer: ExoPlayer
) : RunningComponent {

    private var previousIndex: Int = exoPlayer.currentMediaItemIndex

    override fun onCreate() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                val currentIndex = exoPlayer.currentMediaItemIndex

                if (exoPlayer.currentMediaItem?.mediaId != "SilenceMediaSource") {
                    previousIndex = currentIndex
                    return
                }

                if (currentIndex != previousIndex) {
                    val forward = if (currentIndex > previousIndex || (currentIndex == 0 && previousIndex == exoPlayer.mediaItemCount - 1)) {
                        true
                    } else {
                        false
                    }
                    previousIndex = currentIndex


                    if (forward) {
                        exoPlayer.seekTo(exoPlayer.currentMediaItemIndex + 1, 0)
                    } else {
                        exoPlayer.seekTo(exoPlayer.currentMediaItemIndex - 1, 0)
                    }
                }
            }
        })
    }
}