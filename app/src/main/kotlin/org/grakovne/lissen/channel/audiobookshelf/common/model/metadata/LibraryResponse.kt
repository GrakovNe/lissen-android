package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryResponse(
  val libraries: List<LibraryItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemResponse(
  val id: String,
  val name: String,
  val mediaType: String,
  val displayOrder: Int?,
)
