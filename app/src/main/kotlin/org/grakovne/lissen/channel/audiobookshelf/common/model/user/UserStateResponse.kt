package org.grakovne.lissen.channel.audiobookshelf.common.model.user

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.channel.audiobookshelf.common.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse

@Keep
@JsonClass(generateAdapter = true)
data class UserStateResponse(
  val mediaProgress: List<MediaProgressResponse>?,
  val bookmarks: List<BookmarksItemResponse>?,
)
