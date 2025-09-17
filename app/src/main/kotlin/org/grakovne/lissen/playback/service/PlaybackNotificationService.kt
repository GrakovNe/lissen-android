package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
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
    private val exoPlayer: ExoPlayer,
    private val sharedPreferences: LissenSharedPreferences,
    private val mediaProvider: LissenMediaProvider,
  ) : RunningComponent {
    override fun onCreate() {
      exoPlayer.addListener(
        object : Player.Listener {
          override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
          ) {
            super.onPlayWhenReadyChanged(playWhenReady, reason)

            if (playWhenReady) {
              exoPlayer.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
            }
          }

          override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
          ) {
            val previousIndex = oldPosition.mediaItemIndex
            val currentIndex = newPosition.mediaItemIndex
            val currentItem =
              exoPlayer
                .currentMediaItem
                ?.localConfiguration
                ?.tag as? DetailedItem

            if (null == currentItem) {
              return
            }

            if (exoPlayer.currentMediaItem?.mediaId?.let { isTrackAvailable(it) } != false) {
              return
            }

            if (currentIndex != previousIndex) {
              val direction =
                when (
                  currentIndex > previousIndex ||
                    (currentIndex == 0 && previousIndex == exoPlayer.mediaItemCount - 1)
                ) {
                  true -> Direction.FORWARD
                  false -> Direction.BACKWARD
                }

              val nextTrack =
                findAvailableTrackIndex(exoPlayer.currentMediaItemIndex, direction, exoPlayer, 0)
              nextTrack?.let { exoPlayer.seekTo(it, 0) }

              if (nextTrack == null || nextTrack < currentIndex) {
                exoPlayer.pause()
              }
            }
          }
        },
      )
    }

    private fun findAvailableTrackIndex(
      currentItem: Int,
      direction: Direction,
      exoPlayer: ExoPlayer,
      iteration: Int,
    ): Int? {
      if (isTrackAvailable(exoPlayer.getMediaItemAt(currentItem).mediaId)) {
        return currentItem
      }

      if (iteration > 4096) {
        return null
      }

      val foundItem =
        when (direction) {
          Direction.FORWARD -> (currentItem + 1) % exoPlayer.mediaItemCount
          Direction.BACKWARD -> if (currentItem - 1 < 0) exoPlayer.mediaItemCount - 1 else currentItem - 1
        }

      return findAvailableTrackIndex(foundItem, direction, exoPlayer, iteration + 1)
    }

    private fun isTrackAvailable(fileId: String): Boolean {
      val mediaItemId =
        exoPlayer
          .currentMediaItem
          ?.localConfiguration
          ?.tag as? DetailedItem
          ?: return false

      return mediaProvider
        .provideFileUri(mediaItemId.id, fileId)
        .fold(
          onSuccess = { true },
          onFailure = { false },
        )
    }
  }

private enum class Direction {
  FORWARD,
  BACKWARD,
}
