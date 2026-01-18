package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksItemResponse
import org.grakovne.lissen.lib.domain.Bookmark
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkItemResponseConverter {
  @Inject
  constructor()

  fun apply(item: BookmarksItemResponse): Bookmark =
    Bookmark(
      libraryItemId = item.libraryItemId,
      title = item.title,
      totalPosition = item.time,
      createdAt = item.createdAt,
    )
}
