package org.grakovne.lissen.domain

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
  authorBooks: List<Book>,
  randomBooks: List<Book>,
): FinishedSuggestion? {
  val seriesCandidates =
    when (finished.series.any { it.name.isNotBlank() }) {
      true -> {
        seriesBooks
          .dropWhile { it.id != finished.id }
          .drop(1)
          .filter { it.id != finished.id }
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
    authorBooks
      .filter { it.id != finished.id }
      .distinctBy { it.id }
      .take(MAX_FINISHED_CANDIDATES)

  if (authorCandidates.isNotEmpty()) {
    return FinishedSuggestion(FinishedSuggestionSource.AUTHOR, authorCandidates)
  }

  return randomBooks
    .filter { it.id != finished.id }
    .distinctBy { it.id }
    .take(MAX_FINISHED_CANDIDATES)
    .takeIf { it.isNotEmpty() }
    ?.let { FinishedSuggestion(FinishedSuggestionSource.RANDOM, it) }
}
