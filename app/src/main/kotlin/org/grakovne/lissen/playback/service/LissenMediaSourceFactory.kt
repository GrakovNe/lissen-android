package org.grakovne.lissen.playback.service

import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.parcelize.Parcelize
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS

@Parcelize
data class FileClip(
  val fileId: String,
  val clipStart: Double,
  val clipEnd: Double,
) : Parcelable

@UnstableApi
class LissenMediaSourceFactory(
  private val mediaSourceFactory: DefaultMediaSourceFactory,
) : MediaSource.Factory {
  data class MediaId(
    val bookId: String,
    val chapterId: Int,
  ) {
    override fun toString(): String = "chapter:$bookId:$chapterId"

    companion object {
      private val regex = """chapter:([^/]+):(\d+)$""".toRegex()

      fun fromString(mediaIdStr: String): MediaId? =
        regex.find(mediaIdStr)?.let {
          it.destructured.let { (bookId, chapterIdStr) ->
            MediaId(
              bookId = bookId,
              chapterId = chapterIdStr.toInt(),
            )
          }
        }
    }
  }

  override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
    mediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
    return this
  }

  override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
    mediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    return this
  }

  override fun getSupportedTypes(): IntArray = mediaSourceFactory.supportedTypes

  override fun createMediaSource(mediaItem: MediaItem): MediaSource {
    fun FileClip.toMediaSource(
      bookId: String,
      metadata: MediaMetadata? = null,
    ): MediaSource {
      val source =
        mediaSourceFactory
          .createMediaSource(
            MediaItem
              .Builder()
              .setUri(toLissenUri(bookId, fileId))
              .apply { metadata?.let { setMediaMetadata(it) } }
              .build(),
          )

      val (startUs, endUs) = clipBoundsUs(clipStart, clipEnd)

      return when {
        startUs == 0L && endUs == C.TIME_UNSET -> {
          source
        }

        else -> {
          ClippingMediaSource
            .Builder(source)
            .setStartPositionUs(startUs)
            .setEndPositionUs(endUs)
            .build()
        }
      }
    }

    return MediaId.fromString(mediaItem.mediaId)?.let { (bookId, chapterId) ->
      mediaItem.requestMetadata.extras?.let { extras ->
        BundleCompat.getParcelableArrayList(extras, FILE_SEGMENTS, FileClip::class.java)?.let { segments ->
          when (segments.size) {
            0 -> {
              SilenceMediaSource(1L)
            }

            1 -> {
              segments.first().toMediaSource(bookId, mediaItem.mediaMetadata)
            }

            else -> {
              ConcatenatingMediaSource2
                .Builder()
                .apply {
                  segments.forEach {
                    add(it.toMediaSource(bookId), segmentDurationMs(it.clipStart, it.clipEnd))
                  }
                }.setMediaItem(
                  MediaItem
                    .Builder()
                    .setMediaMetadata(mediaItem.mediaMetadata)
                    .build(),
                ).build()
            }
          }
        }
      }
    } ?: mediaSourceFactory.createMediaSource(mediaItem)
  }

  internal companion object {
    /**
     * Converts clip bounds in seconds to microseconds, tolerating corrupted metadata.
     *
     * Durations coming from the server can be NaN or negative, which previously produced
     * end positions smaller than start positions and made media3 reject the clip with
     * "IllegalArgumentException". Invalid bounds degrade to playing from the start
     * position until the end of the file instead of crashing the playback service.
     */
    internal fun clipBoundsUs(
      clipStart: Double,
      clipEnd: Double,
    ): Pair<Long, Long> {
      val startUs = if (clipStart.isFinite() && clipStart > 0) (clipStart * 1_000_000).toLong() else 0L
      val endUs =
        when {
          !clipEnd.isFinite() || clipEnd <= 0 -> C.TIME_UNSET
          clipEnd * 1_000_000 <= startUs -> C.TIME_UNSET
          else -> (clipEnd * 1_000_000).toLong()
        }

      return startUs to endUs
    }

    internal fun segmentDurationMs(
      clipStart: Double,
      clipEnd: Double,
    ): Long {
      val durationMs = ((clipEnd - clipStart) * 1000).toLong()

      return if (durationMs > 0) durationMs else 1L
    }
  }
}
