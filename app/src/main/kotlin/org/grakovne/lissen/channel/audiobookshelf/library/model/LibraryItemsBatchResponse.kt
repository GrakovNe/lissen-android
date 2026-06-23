package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemsBatchRequest(
  val libraryItemIds: List<String>,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryItemsBatchResponse(
  val libraryItems: List<LibraryItem>,
)
