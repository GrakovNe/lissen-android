package org.grakovne.lissen.domain

import kotlin.random.Random

const val MAX_FINISHED_CANDIDATES = 3

enum class FinishedSuggestionSource {
  SERIES,
  AUTHOR,
  RANDOM,
}

data class FinishedSuggestion(
  val source: FinishedSuggestionSource,
  val candidates: List<Book>,
)

fun selectFinishedSuggestion(
  finished: DetailedItem,
  seriesBooks: List<Book>,
  unlistenedBooks: List<Book>,
  random: Random = Random.Default,
): FinishedSuggestion? {
  val unlistened = unlistenedBooks.filter { it.id != finished.id }.distinctBy { it.id }
  val unlistenedIds = unlistened.map { it.id }.toSet()

  val seriesCandidates =
    when (finished.series.any { it.name.isNotBlank() }) {
      true -> {
        seriesBooks
          .dropWhile { it.id != finished.id }
          .drop(1)
          .filter { it.id in unlistenedIds }
          .distinctBy { it.id }
          .take(MAX_FINISHED_CANDIDATES)
      }

      false -> {
        emptyList()
      }
    }

  if (seriesCandidates.isNotEmpty()) {
    return FinishedSuggestion(FinishedSuggestionSource.SERIES, seriesCandidates)
  }

  val authorCandidates =
    finished.author
      ?.takeIf { it.isNotBlank() }
      ?.let { author -> unlistened.filter { it.author == author } }
      ?.take(MAX_FINISHED_CANDIDATES)
      .orEmpty()

  if (authorCandidates.isNotEmpty()) {
    return FinishedSuggestion(FinishedSuggestionSource.AUTHOR, authorCandidates)
  }

  return unlistened
    .shuffled(random)
    .take(MAX_FINISHED_CANDIDATES)
    .takeIf { it.isNotEmpty() }
    ?.let { FinishedSuggestion(FinishedSuggestionSource.RANDOM, it) }
}
