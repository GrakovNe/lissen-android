package org.grakovne.lissen.content.cache.persistent

import org.grakovne.lissen.lib.domain.AllItemsDownloadOption
import org.grakovne.lissen.lib.domain.CurrentItemDownloadOption
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.DownloadOption
import org.grakovne.lissen.lib.domain.NumberItemDownloadOption
import org.grakovne.lissen.lib.domain.PlayingChapter
import org.grakovne.lissen.lib.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.playback.service.calculateChapterIndex

fun calculateRequestedChapters(
  book: DetailedItem,
  option: DownloadOption,
  currentTotalPosition: Double,
): List<PlayingChapter> {
  val chapterIndex = calculateChapterIndex(book, currentTotalPosition)

  return when (option) {
    AllItemsDownloadOption -> book.chapters
    CurrentItemDownloadOption -> listOfNotNull(book.chapters.getOrNull(chapterIndex))
    is NumberItemDownloadOption ->
      book.chapters.subList(
        chapterIndex.coerceAtLeast(0),
        (chapterIndex + option.itemsNumber).coerceIn(chapterIndex..book.chapters.size),
      )
    RemainingItemsDownloadOption ->
      book.chapters.subList(
        chapterIndex.coerceIn(0, book.chapters.size),
        book.chapters.size,
      )
  }
}
