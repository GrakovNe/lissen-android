package org.grakovne.lissen.common

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

object EpisodeOrderingEngine {
  fun sortChapters(
    chapters: List<PlayingChapter>,
    ordering: EpisodeOrdering,
  ): List<PlayingChapter> {
    val comparator: Comparator<PlayingChapter> =
      when (ordering.key) {
        EpisodeSortKey.PUBLISHED_AT -> {
          comparatorOf({ it.publishedAt }, ordering.ascending)
        }

        EpisodeSortKey.TITLE -> {
          comparatorOf({ it.title }, ordering.ascending) { left, right ->
            String.CASE_INSENSITIVE_ORDER.compare(left, right)
          }
        }

        EpisodeSortKey.SEASON -> {
          comparatorOf({ it.season }, ordering.ascending)
        }

        EpisodeSortKey.EPISODE -> {
          comparatorOf({ it.episodeNumber }, ordering.ascending)
        }

        EpisodeSortKey.FILENAME -> {
          comparatorOf({ it.filename }, ordering.ascending) { left, right ->
            compareNatural(left, right)
          }
        }
      }

    return chapters.sortedWith(comparator)
  }

  // nulls stay last in both directions, so the null handling lives outside
  // the direction flip; equal keys keep the stable (server) order
  private fun <K : Comparable<K>> comparatorOf(
    selector: (PlayingChapter) -> K?,
    ascending: Boolean,
    valueComparator: (K, K) -> Int = { left, right -> left.compareTo(right) },
  ): Comparator<PlayingChapter> =
    Comparator { left, right ->
      val leftKey = selector(left)
      val rightKey = selector(right)

      when {
        leftKey == null && rightKey == null -> 0
        leftKey == null -> 1
        rightKey == null -> -1
        ascending -> valueComparator(leftKey, rightKey)
        else -> valueComparator(rightKey, leftKey)
      }
    }

  fun recomputeOffsets(chapters: List<PlayingChapter>): List<PlayingChapter> {
    var accumulated = 0.0

    return chapters.map { chapter ->
      chapter
        .copy(start = accumulated, end = accumulated + chapter.duration)
        .also { accumulated += chapter.duration }
    }
  }

  fun applyOrdering(
    chapters: List<PlayingChapter>,
    ordering: EpisodeOrdering,
  ): List<PlayingChapter> = recomputeOffsets(sortChapters(chapters, ordering))

  fun reorder(
    item: DetailedItem,
    ordering: EpisodeOrdering,
  ): DetailedItem {
    val ordered = applyOrdering(item.chapters, ordering)

    val progress =
      item.progress?.let { currentProgress ->
        rebaseProgress(
          previous = item.chapters,
          ordered = ordered,
          currentTime = currentProgress.currentTime,
        )?.let { rebasedTime -> currentProgress.copy(currentTime = rebasedTime) }
          ?: currentProgress
      }

    return item.copy(chapters = ordered, progress = progress)
  }

  private fun rebaseProgress(
    previous: List<PlayingChapter>,
    ordered: List<PlayingChapter>,
    currentTime: Double,
  ): Double? {
    val anchor =
      previous.firstOrNull { currentTime < it.end }
        ?: previous.lastOrNull()
        ?: return null

    val target = ordered.firstOrNull { it.id == anchor.id } ?: return null
    val positionWithinEpisode = (currentTime - anchor.start).coerceIn(0.0, anchor.duration)

    return target.start + positionWithinEpisode
  }

  internal fun compareNatural(
    left: String,
    right: String,
  ): Int {
    var leftIndex = 0
    var rightIndex = 0

    while (leftIndex < left.length && rightIndex < right.length) {
      val leftChar = left[leftIndex]
      val rightChar = right[rightIndex]

      when (leftChar.isDigit() && rightChar.isDigit()) {
        true -> {
          var leftDigitsEnd = leftIndex
          while (leftDigitsEnd < left.length && left[leftDigitsEnd].isDigit()) leftDigitsEnd++

          var rightDigitsEnd = rightIndex
          while (rightDigitsEnd < right.length && right[rightDigitsEnd].isDigit()) rightDigitsEnd++

          val comparison = compareDigitSegments(left.substring(leftIndex, leftDigitsEnd), right.substring(rightIndex, rightDigitsEnd))
          if (comparison != 0) return comparison

          leftIndex = leftDigitsEnd
          rightIndex = rightDigitsEnd
        }

        false -> {
          val caseInsensitive = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
          if (caseInsensitive != 0) return caseInsensitive

          leftIndex++
          rightIndex++
        }
      }
    }

    val lengthComparison = (left.length - leftIndex).compareTo(right.length - rightIndex)
    if (lengthComparison != 0) return lengthComparison

    return left.compareTo(right)
  }

  private fun compareDigitSegments(
    left: String,
    right: String,
  ): Int {
    val leftSignificant = left.trimStart('0')
    val rightSignificant = right.trimStart('0')

    if (leftSignificant.length != rightSignificant.length) {
      return leftSignificant.length.compareTo(rightSignificant.length)
    }

    val byValue = leftSignificant.compareTo(rightSignificant)
    if (byValue != 0) return byValue

    return left.length.compareTo(right.length)
  }
}
