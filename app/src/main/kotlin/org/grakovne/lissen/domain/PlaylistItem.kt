package org.grakovne.lissen.domain

import androidx.annotation.Keep

/** A row of a playlist. [book] is the library item the row belongs to, for an episode its podcast. */
@Keep
sealed interface PlaylistItem {
  val book: Book

  @Keep
  data class BookItem(
    override val book: Book,
  ) : PlaylistItem

  @Keep
  data class EpisodeItem(
    override val book: Book,
    val episodeId: String,
    val title: String,
  ) : PlaylistItem
}
