package org.grakovne.lissen.content.cache

import org.grakovne.lissen.lib.domain.BookFile
import org.grakovne.lissen.lib.domain.PlayingChapter

fun findRelatedFiles(
  chapter: PlayingChapter,
  files: List<BookFile>,
): List<BookFile> {
  val chapterStartRounded = chapter.start.round()
  val chapterEndRounded = chapter.end.round()

  val startTimes =
    files
      .runningFold(0.0) { acc, file -> acc + file.duration }
      .dropLast(1)

  val fileStartTimes = files.zip(startTimes)

  return fileStartTimes
    .filter { (file, fileStartTime) ->
      val fileStartTimeRounded = fileStartTime.round()
      val fileEndTimeRounded = (fileStartTime + file.duration).round()

      fileStartTimeRounded < chapterEndRounded && chapterStartRounded < fileEndTimeRounded
    }.map { it.first }
}

private const val PRECISION = 0.01

private fun Double.round(): Double = kotlin.math.round(this / PRECISION) * PRECISION
