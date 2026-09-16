package org.grakovne.lissen.common

enum class EpisodeSortKey {
  PUBLISHED_AT,
  TITLE,
  SEASON,
  EPISODE,
  FILENAME,
}

data class EpisodeOrdering(
  val key: EpisodeSortKey = EpisodeSortKey.PUBLISHED_AT,
  val ascending: Boolean = true,
) {
  companion object {
    val DEFAULT = EpisodeOrdering(key = EpisodeSortKey.PUBLISHED_AT, ascending = true)
  }
}

fun EpisodeOrdering.selectKey(key: EpisodeSortKey): EpisodeOrdering =
  when (key == this.key) {
    true -> copy(ascending = !ascending)
    false -> EpisodeOrdering(key = key, ascending = key.defaultDirection)
  }

val EpisodeSortKey.defaultDirection: Boolean
  get() =
    when (this) {
      EpisodeSortKey.PUBLISHED_AT -> false

      EpisodeSortKey.TITLE,
      EpisodeSortKey.SEASON,
      EpisodeSortKey.EPISODE,
      EpisodeSortKey.FILENAME,
      -> true
    }
