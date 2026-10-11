package org.grakovne.lissen.domain

import androidx.annotation.Keep

/** [book] is the row's library item; for an episode, its podcast. */
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
