package org.grakovne.lissen.channel.audiobookshelf.common.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.bookmark.BookmarksResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.ConnectionInfoResponse
import org.grakovne.lissen.channel.common.ConnectionInfo
import org.grakovne.lissen.lib.domain.Bookmark
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarksResponseConverter
  @Inject
  constructor(
    private val itemConverter: BookmarkItemResponseConverter,
  ) {
    fun apply(response: BookmarksResponse): List<Bookmark> =
      response
        .user
        .bookmarks
        .map { itemConverter.apply(it) }
  }
