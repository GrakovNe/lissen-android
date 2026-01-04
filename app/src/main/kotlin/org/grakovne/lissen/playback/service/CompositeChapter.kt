package org.grakovne.lissen.playback.service

import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter

data class CompositeChapter(
  val chapter: PlayingChapter,
  val files: List<BookFile>,
  val startPosition: Long,
  val endPosition: Long,
) {
  companion object {
    fun mapFilesToChapters(book: DetailedItem): List<CompositeChapter> {
      var time = 0.0
      var i = 0
      var j = 0

      return arrayListOf<CompositeChapter>().apply {
        while (i < book.chapters.size && j < book.files.size) {
          val chapter = book.chapters[i++]
          val startTime = chapter.start - time

          val files =
            arrayListOf<BookFile>().apply {
              while (time < chapter.end) {
                val file = book.files[j]
                add(file)

                if (time + file.duration >= chapter.end) {
                  break
                }

                time += file.duration
                j++
              }
            }

          add(
            CompositeChapter(
              chapter,
              files,
              (startTime * 1000).toLong(),
              ((startTime + chapter.end) * 1000).toLong(),
            ),
          )
        }
      }
    }
  }
}
