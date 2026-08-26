package org.grakovne.lissen.playback.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import org.grakovne.lissen.content.ExternalCoverProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.service.PlaybackService.Companion.CHAPTER_START_MS
import org.grakovne.lissen.playback.service.PlaybackService.Companion.FILE_SEGMENTS

@UnstableApi
fun bookToChapterMediaItems(book: DetailedItem): MediaItemsWithStartPosition {
  var (chapterIndex, chapterOffset) =
    book
      .progress
      ?.currentTime
      ?.let { calculateChapterIndexAndPosition(book, it) }
      ?: ChapterPosition(0, 0.0)

  val negativeChapter = chapterIndex < 0
  val lastMoments =
    !negativeChapter &&
      book.chapters.isNotEmpty() &&
      (book.chapters.last().end - 5) < (book.progress?.currentTime ?: 0.0)

  if (negativeChapter || lastMoments) {
    chapterIndex = 0
    chapterOffset = 0.0
  }

  val chapterMediaItems =
    PlaybackService.resolveChapterToFiles(chapters = book.chapters, files = book.files) { index, chapter, resolvedFiles ->
      MediaItem
        .Builder()
        .setMediaId(LissenMediaSourceFactory.MediaId(book.id, index).toString())
        .setRequestMetadata(
          MediaItem.RequestMetadata
            .Builder()
            .setExtras(Bundle().apply { putParcelableArrayList(FILE_SEGMENTS, resolvedFiles) })
            .build(),
        ).setMediaMetadata(
          MediaMetadata
            .Builder()
            .setAlbumTitle(book.title)
            .setTitle(chapter.title)
            .setArtist(book.title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(ExternalCoverProvider.bookCoverUri(book.id))
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setExtras(Bundle().apply { putLong(CHAPTER_START_MS, (chapter.start * 1000).toLong()) })
            .build(),
        ).setTag(book)
        .build()
    }

  return MediaItemsWithStartPosition(chapterMediaItems, chapterIndex, (chapterOffset * 1000).toLong())
}
