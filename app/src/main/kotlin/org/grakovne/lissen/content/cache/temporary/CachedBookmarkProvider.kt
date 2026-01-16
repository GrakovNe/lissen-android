package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.lib.domain.Bookmark

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor() {
    private val _memCache = mutableMapOf<String, List<Bookmark>>()

    fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val bookmark =
        Bookmark(
          id = "testId",
          title = "Test bookmark",
          createdAt = 123,
          totalPosition = 12356.0,
          libraryItemId = libraryItemId,
        )

      val current = _memCache[libraryItemId] ?: emptyList()

      val updated = current + bookmark
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
          id = "testId",
          title = buildTitle(currentChapter, chapterTime),
          createdAt = 123,
          totalPosition = totalTime,
          libraryItemId = libraryItemId,
        )

      val updated = _memCache[libraryItemId].orEmpty() + bookmark
      _memCache[libraryItemId] = updated
    }

    private fun buildTitle(
      currentChapter: String,
      time: Double,
    ): String = "$currentChapter - ${time.toInt()}"

    fun removeBookmark(
      libraryItemId: String,
      bookmarkId: String,
    ) {
      val current = _memCache[libraryItemId] ?: return
      _memCache[libraryItemId] = current.filterNot { it.id == bookmarkId }
    }
  }
