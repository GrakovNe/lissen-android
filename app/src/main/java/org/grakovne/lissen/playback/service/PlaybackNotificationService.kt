package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

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
                    val forward = currentIndex > previousIndex || (currentIndex == 0 && previousIndex == exoPlayer.mediaItemCount - 1)
                    previousIndex = currentIndex

                    val targetIndex = when (forward) {
                        true -> min(exoPlayer.currentMediaItemIndex + 1, exoPlayer.mediaItemCount)
                        false -> max(0, exoPlayer.currentMediaItemIndex - 1)
                    }

                    exoPlayer.seekTo(targetIndex, 0)
                }
            }
        })
    }
}