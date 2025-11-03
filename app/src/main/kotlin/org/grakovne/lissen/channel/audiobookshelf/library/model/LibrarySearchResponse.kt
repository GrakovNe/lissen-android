package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibrarySearchResponse(
  val book: List<LibrarySearchItemResponse>,
  val authors: List<LibrarySearchAuthorResponse>,
  val series: List<LibrarySearchSeriesResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibrarySearchItemResponse(
  val libraryItem: LibraryItem,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibrarySearchAuthorResponse(
  val id: String,
  val name: String,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibrarySearchSeriesResponse(
  val books: List<LibraryItem>,
)
