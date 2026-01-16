package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.lib.domain.Bookmark

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor() {
    private val _memCache = mutableMapOf<String, List<Bookmark>>()
    val bookmarks: Map<String, List<Bookmark>> = _memCache

    fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val bookmark =
        Bookmark(
          id = libraryItemId,
          title = "Test bookmark",
          createdAt = 123,
          time = 12356,
          libraryItemId = libraryItemId,
        )

      val current = _memCache[libraryItemId] ?: emptyList()

      val updated = current + bookmark
      _memCache[libraryItemId] = updated

      return updated
    }

    fun createBookmark(
      libraryItemId: String,
      bookmark: Bookmark,
    ) {
      val updated = _memCache[libraryItemId].orEmpty() + bookmark
      _memCache[libraryItemId] = updated
    }

    fun removeBookmark(
      libraryItemId: String,
      bookmarkId: String,
    ) {
      val current = _memCache[libraryItemId] ?: return
      _memCache[libraryItemId] = current.filterNot { it.id == bookmarkId }
    }
  }
