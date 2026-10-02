package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryStatsResponse(
  val genresWithCount: List<LibraryGenreItem>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryGenreItem(
  val genre: String,
  val count: Int,
)
