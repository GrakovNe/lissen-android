package org.grakovne.lissen.content.cache

import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.PlayingChapter
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
  }
}
