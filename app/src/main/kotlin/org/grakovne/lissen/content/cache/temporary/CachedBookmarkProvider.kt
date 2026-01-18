package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.CreateBookmarkRequest
import org.grakovne.lissen.lib.domain.isSame

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor(
    private val channelProvider: AudiobookshelfChannelProvider,
  ) {
    private val _memCache = mutableMapOf<String, List<Bookmark>>()

    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val updated =
        channelProvider
          .provideMediaChannel()
          .fetchBookmarks(libraryItemId)
          .fold(
            onSuccess = { it },
            onFailure = { _memCache[libraryItemId] ?: emptyList() },
          )

      _memCache[libraryItemId] = updated
      return updated
    }

    suspend fun createBookmark(
      chapterTime: Double,
      totalTime: Double,
      libraryItemId: String,
      currentChapter: String,
    ): Bookmark? = channelProvider
      .provideMediaChannel()
      .createBookmark(
        CreateBookmarkRequest(
          title = buildBookmarkTitle(currentChapter, chapterTime),
          time = totalTime.toInt(),
          libraryItemId = libraryItemId,
        ),
      )
      .fold(
        onSuccess = { it },
        onFailure = { null },
      )

    fun dropBookmark(bookmark: Bookmark) {
      val current = _memCache[bookmark.libraryItemId] ?: return
      _memCache[bookmark.libraryItemId] = current.filterNot { it.isSame(bookmark) }
    }
  }
