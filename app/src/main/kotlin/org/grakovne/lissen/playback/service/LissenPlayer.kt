package org.grakovne.lissen.playback.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import org.grakovne.lissen.lib.domain.PlayingChapter
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min

@OptIn(UnstableApi::class)
class LissenPlayer(
  private val player: ExoPlayer,
) : ForwardingPlayer(player) {
  private var playingChapters: List<PlayingChapter> = listOf()

  private var mediaSources: List<MediaSource> = listOf()

  private var cachedRange = (0..0)

  var currentIndex: Int = 0
    private set

  init {
    player.addListener(object : Player.Listener {
      override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
      ) {
        if (reason == MEDIA_ITEM_TRANSITION_REASON_AUTO) {
          precacheIfNeeded(currentIndex + 1)
          currentIndex++
        }
      }
    })
  }

  fun setMediaSources(
    sources: List<MediaSource>,
    chapters: List<PlayingChapter>,
  ) {
    playingChapters = chapters
    mediaSources = sources

    val count = min(PLAYBACK_BUFFER_ITEMS, sources.lastIndex)
    player.setMediaSources(sources.subList(0, count + 1), false)
    cachedRange = 0..count
  }

  fun seekTo(position: Double?) {
    val position = max(position ?: 0.0, 0.0)

    if (playingChapters.isEmpty() || mediaSources.isEmpty()) {
      Timber.w("Attempted to seek on an empty playlist")
      return
    }

    if (position > playingChapters[playingChapters.lastIndex].end) {
      Timber.w("Attempted to seek past the last chapter")
      return
    }

    val mediaItemIndex = playingChapters.indexOfFirst { it.end > position }
    val positionMs = ((position - playingChapters[mediaItemIndex].start) * 1000).toLong()

    seekTo(mediaItemIndex, positionMs)
  }

  private fun precacheIfNeeded(mediaItemIndex: Int) {
    val offset = mediaItemIndex - currentIndex
    val end = cachedRange.last + offset

    if (offset <= 0 ||
      mediaItemIndex + PLAYBACK_BUFFER_ITEMS <= cachedRange.last ||
      end > mediaSources.lastIndex
    ) {
      return
    }

    val sources = mediaSources.subList(cachedRange.last + 1, end + 1)
    cachedRange = cachedRange.first..end
    player.addMediaSources(sources)
  }

  override fun seekTo(
    mediaItemIndex: Int,
    positionMs: Long,
  ) {
    when (mediaItemIndex) {
      currentIndex ->
        super.seekTo(positionMs)

      in cachedRange -> {
        super.seekTo(
          player.currentMediaItemIndex + mediaItemIndex - currentIndex,
          positionMs,
        )

        precacheIfNeeded(mediaItemIndex)
      }

      else -> {
        val start = max(0, mediaItemIndex - PLAYBACK_BUFFER_ITEMS)
        val end = min(mediaSources.lastIndex, mediaItemIndex + PLAYBACK_BUFFER_ITEMS)
        val sources = mediaSources.subList(start, end + 1)

        cachedRange = start..end
        player.setMediaSources(sources, false)
        super.seekTo(PLAYBACK_BUFFER_ITEMS, positionMs)
      }
    }

    currentIndex = mediaItemIndex
  }

  override fun seekToNext() {
    val index =
      (currentIndex + 1).let {
        if (it > mediaSources.size) 0 else it
      }
    seekTo(index, 0)
  }

  override fun seekToPrevious() {
    seekTo(max(currentIndex - 1, 0), 0)
  }

  val currentPositionAbsolute: Long
    get() {
      return (playingChapters.take(currentIndex).sumOf { it.duration * 1000 } + currentPosition).toLong()
    }

  companion object {
    private const val PLAYBACK_BUFFER_ITEMS = 4
  }
}
