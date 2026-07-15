package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.core.os.BundleCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlaybackNavigationService
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val sharedPreferences: PlaybackPreferences,
    private val mediaProvider: LissenMediaProvider,
    private val playbackSynchronizationService: PlaybackSynchronizationService,
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

            if (isTrackAvailable(exoPlayer.currentMediaItemIndex)) {
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

              val nextTrack = findAvailableTrackIndex(exoPlayer.currentMediaItemIndex, direction, exoPlayer)
              nextTrack?.let { exoPlayer.seekTo(it, 0) }

              if (nextTrack == null || nextTrack < currentIndex) {
                exoPlayer.pause()
                playbackSynchronizationService.cancelSynchronization()
              }
            }
          }
        },
      )
    }

    private fun findAvailableTrackIndex(
      startIndex: Int,
      direction: Direction,
      exoPlayer: ExoPlayer,
    ): Int? {
      val count = exoPlayer.mediaItemCount
      if (count == 0) {
        return null
      }

      var index = startIndex
      repeat(count) {
        if (isTrackAvailable(index)) {
          return index
        }

        index =
          when (direction) {
            Direction.FORWARD -> (index + 1) % count
            Direction.BACKWARD -> if (index - 1 < 0) count - 1 else index - 1
          }
      }

      return null
    }

    private fun isTrackAvailable(index: Int): Boolean {
      if (index < 0 || index >= exoPlayer.mediaItemCount) {
        return false
      }

      val mediaItem = exoPlayer.getMediaItemAt(index)

      val bookId =
        LissenMediaSourceFactory.MediaId
          .fromString(mediaItem.mediaId)
          ?.bookId
          ?: return false

      val segments =
        mediaItem
          .requestMetadata
          .extras
          ?.let { BundleCompat.getParcelableArrayList(it, PlaybackService.FILE_SEGMENTS, FileClip::class.java) }
          ?: return false

      if (segments.isEmpty()) {
        return false
      }

      return segments.all { clip ->
        mediaProvider
          .provideFileUri(bookId, clip.fileId)
          .fold(
            onSuccess = { true },
            onFailure = { false },
          )
      }
    }
  }

private enum class Direction {
  FORWARD,
  BACKWARD,
}
