package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibrariesResponse(
  val libraries: List<LibraryItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryResponse(
  val library: LibraryItemResponse,
  val filterdata: FilterdataResponse,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemResponse(
  val id: String,
  val name: String,
  val mediaType: String,
  val displayOrder: Int?,
)

@Keep
@JsonClass(generateAdapter = true)
data class FilterdataResponse(
  val authors: List<NamedIdResponse>,
  val genres: List<String>,
  val tags: List<String>,
  val series: List<NamedIdResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class NamedIdResponse(
  val id: String,
  val name: String,
)
