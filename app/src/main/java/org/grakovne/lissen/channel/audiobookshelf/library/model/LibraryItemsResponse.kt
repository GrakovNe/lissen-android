package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep

@Keep
data class LibraryItemsResponse(
  val results: List<LibraryItem>,
  val page: Int,
)

@Keep
data class LibraryItem(
  val id: String,
  val media: Media,
)

@Keep
data class Media(
  val duration: Double,
  val metadata: LibraryMetadata,
)

@Keep
data class LibraryMetadata(
  val title: String?,
  val subtitle: String?,
  val seriesName: String?,
  val authorName: String?,
)
