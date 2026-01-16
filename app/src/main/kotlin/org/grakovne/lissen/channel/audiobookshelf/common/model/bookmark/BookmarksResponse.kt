package org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class BookmarksResponse(
  val user: BookmarksUserResponse,
)

@Keep
@JsonClass(generateAdapter = true)
data class BookmarksUserResponse(
  val bookmarks: List<BookmarksItemResponse>,
)

@Keep
@JsonClass(generateAdapter = true)
data class BookmarksItemResponse(
  val libraryItemId: String,
  val time: Double,
  val title: String,
  val createdAt: Long,
)
