package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.ExoPlayerProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackNotificationService
  @Inject
  constructor(
    private val exoPlayerProvider: ExoPlayerProvider,
    private val sharedPreferences: LissenSharedPreferences,
    private val mediaProvider: LissenMediaProvider,
  ) : RunningComponent {
    override fun onCreate() {
      val player = exoPlayerProvider.provideExoPlayer()

      player
        .addListener(
          object : Player.Listener {
            override fun onPlayWhenReadyChanged(
              playWhenReady: Boolean,
              reason: Int,
            ) {
              super.onPlayWhenReadyChanged(playWhenReady, reason)

              if (playWhenReady) {
                player.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
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
                player
                  .currentMediaItem
                  ?.localConfiguration
                  ?.tag as? DetailedItem

              if (null == currentItem) {
                return
              }

              if (player.currentMediaItem?.mediaId?.let { isTrackAvailable(it) } != false) {
                return
              }

              if (currentIndex != previousIndex) {
                val direction =
                  when (
                    currentIndex > previousIndex ||
                      (currentIndex == 0 && previousIndex == player.mediaItemCount - 1)
                  ) {
                    true -> Direction.FORWARD
                    false -> Direction.BACKWARD
                  }

                val nextTrack =
                  findAvailableTrackIndex(player.currentMediaItemIndex, direction, player, 0)
                nextTrack?.let { player.seekTo(it, 0) }

                if (nextTrack == null || nextTrack < currentIndex) {
                  player.pause()
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
      val player = exoPlayerProvider.provideExoPlayer()

      val mediaItem =
        player
          .currentMediaItem
          ?.localConfiguration
          ?.tag as? DetailedItem
          ?: return false

      return mediaProvider
        .provideFileUri(mediaItem.id, fileId)
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
