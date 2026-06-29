package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryAuthorsResponse(
  val results: List<LibraryAuthorItem>,
  val page: Int,
  val total: Int,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryAuthorItem(
  val id: String,
  val name: String,
  val numBooks: Int?,
)
