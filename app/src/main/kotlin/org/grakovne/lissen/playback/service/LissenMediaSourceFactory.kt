package org.grakovne.lissen.playback.service

import android.os.Parcelable
import androidx.core.os.BundleCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.ClippingMediaSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.parcelize.Parcelize
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS
import timber.log.Timber

@Parcelize
data class FileClip(
  val fileId: String,
  val clipStart: Double,
  val clipEnd: Double,
) : Parcelable

@UnstableApi
class LissenMediaSourceFactory(
  private val progressiveMediaSourceFactory: ProgressiveMediaSource.Factory,
) : MediaSource.Factory {
  override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
    progressiveMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
    return this
  }

  override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
    progressiveMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    return this
  }

  override fun getSupportedTypes(): IntArray = progressiveMediaSourceFactory.supportedTypes

  companion object {
    private data class ChapterMetadata(
      val bookId: String,
      val chapterId: Int,
    )

    private val regex = """chapter:([^/]+):(\d+)$""".toRegex()

    private fun String.parseChapterMetadata(): ChapterMetadata? =
      regex.find(this)?.let {
        it.destructured.let { (bookId, chapterIdStr) ->
          ChapterMetadata(
            bookId = bookId,
            chapterId = chapterIdStr.toInt(),
          )
        }
      }

    fun getMediaId(
      bookId: String,
      chapterId: Int,
    ) = "chapter:$bookId:$chapterId"
  }

  override fun createMediaSource(mediaItem: MediaItem): MediaSource {
    fun FileClip.toMediaSource(
      bookId: String,
      metadata: MediaMetadata? = null,
    ): MediaSource =
      progressiveMediaSourceFactory
        .createMediaSource(
          MediaItem
            .Builder()
            .setUri(apply(bookId, fileId))
            .apply { metadata?.let { setMediaMetadata(it) } }
            .build(),
        ).let {
          ClippingMediaSource
            .Builder(it)
            .setStartPositionUs((clipStart * 1_000_000).toLong())
            .setEndPositionUs((clipEnd * 1_000_000).toLong())
            .build()
        }

    return mediaItem.mediaId.parseChapterMetadata()?.let { (bookId, chapterId) ->
      mediaItem.requestMetadata.extras?.let { extras ->
        BundleCompat.getParcelableArrayList(extras, FILE_SEGMENTS, FileClip::class.java)?.let { segments ->
          Timber.d("Created media source for chapter [${mediaItem.mediaId}] from ${segments.size} file segments")
          segments.singleOrNull()?.toMediaSource(bookId, mediaItem.mediaMetadata)
            ?: ConcatenatingMediaSource2
              .Builder()
              .apply {
                segments.forEach {
                  add(it.toMediaSource(bookId), ((it.clipEnd - it.clipStart) * 1000).toLong())
                }
              }.setMediaItem(
                MediaItem
                  .Builder()
                  .setMediaMetadata(mediaItem.mediaMetadata)
                  .build(),
              ).build()
        }
      }
    } ?: progressiveMediaSourceFactory.createMediaSource(mediaItem)
  }
}
