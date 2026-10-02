package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

// the library stats endpoint is the only one that counts books per genre; the rest of its payload is ignored
@Keep
@JsonClass(generateAdapter = true)
data class LibraryGenresResponse(
  val genresWithCount: List<LibraryGenreItem>?,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryGenreItem(
  val genre: String,
  val count: Int?,
)
