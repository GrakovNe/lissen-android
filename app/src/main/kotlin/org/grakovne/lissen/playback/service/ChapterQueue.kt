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
fun bookToMediaItems(book: DetailedItem): MediaItemsWithStartPosition =
  if (book.hasServerChapters) {
    bookToChapterMediaItems(book)
  } else {
    bookToFileMediaItems(book)
  }

@UnstableApi
fun bookToChapterMediaItems(book: DetailedItem): MediaItemsWithStartPosition {
  val (chapterIndex, chapterOffset) = playbackStartPosition(book)

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

@UnstableApi
private fun bookToFileMediaItems(book: DetailedItem): MediaItemsWithStartPosition {
  val chapterPosition = playbackStartPosition(book)

  var fileStart = 0.0
  val fileMediaItems =
    book.files.mapIndexed { index, file ->
      val currentFileStart = fileStart
      fileStart += file.duration

      MediaItem
        .Builder()
        .setMediaId(LissenMediaSourceFactory.MediaId(book.id, index).toString())
        .setRequestMetadata(
          MediaItem.RequestMetadata
            .Builder()
            .setExtras(
              Bundle().apply {
                putParcelableArrayList(FILE_SEGMENTS, arrayListOf(FileClip(file.id, 0.0, file.duration)))
              },
            ).build(),
        ).setMediaMetadata(
          MediaMetadata
            .Builder()
            .setAlbumTitle(book.title)
            .setTitle(file.name)
            .setArtist(book.title)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setArtworkUri(ExternalCoverProvider.bookCoverUri(book.id))
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
            .setExtras(Bundle().apply { putLong(CHAPTER_START_MS, (currentFileStart * 1000).toLong()) })
            .build(),
        ).setTag(book)
        .build()
    }

  return MediaItemsWithStartPosition(
    fileMediaItems,
    chapterPosition.index,
    (chapterPosition.position * 1000).toLong(),
  )
}

private fun playbackStartPosition(book: DetailedItem): ChapterPosition {
  val position =
    book
      .progress
      ?.currentTime
      ?.let { calculateChapterIndexAndPosition(book, it) }
      ?: ChapterPosition(0, 0.0)
  val lastMoments =
    position.index >= 0 &&
      book.chapters.isNotEmpty() &&
      (book.chapters.last().end - 5) < (book.progress?.currentTime ?: 0.0)

  return position.takeUnless { it.index < 0 || lastMoments } ?: ChapterPosition(0, 0.0)
}
