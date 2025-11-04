package org.grakovne.lissen.channel.audiobookshelf.common.model.metadata

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.channel.audiobookshelf.library.model.LibraryItem

@Keep
@JsonClass(generateAdapter = true)
data class AuthorItemsResponse(
  val libraryItems: List<LibraryItem>,
)
