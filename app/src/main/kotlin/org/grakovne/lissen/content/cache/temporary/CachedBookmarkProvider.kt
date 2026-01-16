package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.isSame

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor(
    private val channelProvider: AudiobookshelfChannelProvider,
  ) {
    private val _memCache = mutableMapOf<String, List<Bookmark>>()

    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val fetchedBookmarks =
        channelProvider
          .provideMediaChannel()
          .fetchBookmarks(libraryItemId)
          .fold(
            onSuccess = { it },
            onFailure = { emptyList() },
          )

      val current = _memCache[libraryItemId] ?: emptyList()

      val updated =
        (current + fetchedBookmarks)
          .distinctBy { b -> (current + fetchedBookmarks).indexOfFirst { it.isSame(b) } }

      _memCache[libraryItemId] = updated

      return updated
    }

    fun createBookmark(
      chapterTime: Double,
      totalTime: Double,
      libraryItemId: String,
      currentChapter: String,
    ) {
      val bookmark =
        Bookmark(
          title = buildBookmarkTitle(currentChapter, chapterTime),
          createdAt = 123,
          totalPosition = totalTime,
          libraryItemId = libraryItemId,
        )

      val updated = listOf(bookmark) + _memCache[libraryItemId].orEmpty()
      _memCache[libraryItemId] = updated
    }

    fun dropBookmark(bookmark: Bookmark) {
      val current = _memCache[bookmark.libraryItemId] ?: return
      _memCache[bookmark.libraryItemId] = current.filterNot { it.isSame(bookmark) }
    }
  }
