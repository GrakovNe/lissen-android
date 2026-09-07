package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemsResponse(
  val results: List<LibraryItem>,
  val page: Int,
  val total: Int,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItem(
  val id: String,
  val media: Media,
  val collapsedSeries: CollapsedSeries? = null,
)

@Keep
@JsonClass(generateAdapter = true)
data class CollapsedSeries(
  val id: String,
  val name: String,
  val numBooks: Int?,
  val libraryItemIds: List<String>? = null,
)

@Keep
@JsonClass(generateAdapter = true)
data class Media(
  val numChapters: Int?,
  val metadata: LibraryMetadata,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryMetadata(
  val title: String?,
  val subtitle: String?,
  val seriesName: String?,
  val authorName: String?,
)
