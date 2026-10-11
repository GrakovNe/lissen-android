package org.grakovne.lissen.domain

import androidx.annotation.Keep

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
