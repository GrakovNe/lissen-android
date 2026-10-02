package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryNarratorsResponse(
  val narrators: List<LibraryNarratorItem>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryNarratorItem(
  val name: String,
  val numBooks: Int?,
)
