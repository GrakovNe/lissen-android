package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.SilenceMediaSource
import org.grakovne.lissen.common.RunningComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackNotificationService @Inject constructor(
    private val exoPlayer: ExoPlayer,
) : RunningComponent {

    override fun onCreate() {
        exoPlayer.addListener(object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val previousIndex = oldPosition.mediaItemIndex
                val currentIndex = newPosition.mediaItemIndex

                if (exoPlayer.currentMediaItem?.mediaId != SilenceMediaSource::class.simpleName) {
                    return
                }

                if (currentIndex != previousIndex) {
                    val direction = when (currentIndex > previousIndex || (currentIndex == 0 && previousIndex == exoPlayer.mediaItemCount - 1)) {
                        true -> Direction.FORWARD
                        false -> Direction.BACKWARD
                    }

                    val nextTrack = findAvailableTrackIndex(exoPlayer.currentMediaItemIndex, direction, exoPlayer)
                    exoPlayer.seekTo(nextTrack, 0)

                    if (nextTrack < currentIndex) {
                        exoPlayer.pause()
                    }
                }
            }
        })
    }

    private fun findAvailableTrackIndex(currentItem: Int, direction: Direction, exoPlayer: ExoPlayer): Int {
        if (exoPlayer.getMediaItemAt(currentItem).mediaId != SilenceMediaSource::class.simpleName) {
            return currentItem
        }

        val foundItem = when (direction) {
            Direction.FORWARD -> (currentItem + 1) % exoPlayer.mediaItemCount
            Direction.BACKWARD -> if (currentItem - 1 < 0) exoPlayer.mediaItemCount - 1 else currentItem - 1
        }

        return findAvailableTrackIndex(foundItem, direction, exoPlayer)
    }
}

private enum class Direction {
    FORWARD,
    BACKWARD,
}
