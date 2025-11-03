package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemsResponse(
  val results: List<LibraryItem>,
  val page: Int,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItem(
  val id: String,
  val media: Media,
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
