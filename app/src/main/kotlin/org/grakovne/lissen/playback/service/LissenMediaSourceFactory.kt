package org.grakovne.lissen.playback.service

import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
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

  /**
   * The player reports the MediaItem of the MediaSource we return here, not the one it was given:
   * both getCurrentMediaItem() and getMediaItemAt() read Timeline.Window.mediaItem, and that comes
   * from the created source. So every source we build must carry the chapter item's identity
   * (mediaId, requestMetadata with FILE_SEGMENTS, mediaMetadata with CHAPTER_START_MS) or it becomes
   * invisible to PlaybackNavigationService and PlaybackSynchronizationService. Do not drop it.
   */
  override fun createMediaSource(mediaItem: MediaItem): MediaSource {
    fun FileClip.toMediaSource(
      bookId: String,
      template: MediaItem? = null,
    ): MediaSource {
      val innerItem =
        (template?.buildUpon() ?: MediaItem.Builder())
          .setUri(toLissenUri(bookId, fileId))
          .build()

      return mediaSourceFactory
        .createMediaSource(innerItem)
        .let {
          ClippingMediaSource
            .Builder(it)
            .setStartPositionUs((clipStart * 1_000_000).toLong())
            .setEndPositionUs((clipEnd * 1_000_000).toLong())
            .build()
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
              segments.first().toMediaSource(bookId, mediaItem)
            }

            else -> {
              ConcatenatingMediaSource2
                .Builder()
                .apply {
                  segments.forEach {
                    add(it.toMediaSource(bookId), ((it.clipEnd - it.clipStart) * 1000).toLong())
                  }
                }.setMediaItem(mediaItem)
                .build()
            }
          }
        }
      }
    } ?: mediaSourceFactory.createMediaSource(mediaItem)
  }
}
