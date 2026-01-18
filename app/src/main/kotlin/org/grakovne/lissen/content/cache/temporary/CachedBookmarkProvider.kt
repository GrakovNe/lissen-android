package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
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
  
  fun provideBookmarks(libraryItemId: String): List<Bookmark> = _memCache[libraryItemId] ?: emptyList()
  
  suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> =
    channelProvider
      .provideMediaChannel()
      .fetchBookmarks(libraryItemId)
      .fold(
        onSuccess = {
          _memCache[libraryItemId] = it
          provideBookmarks(libraryItemId)
        },
        onFailure = { provideBookmarks(libraryItemId) },
      )
  
  suspend fun createBookmark(
    chapterTime: Double,
    totalTime: Double,
    libraryItemId: String,
    currentChapter: String,
  ): Bookmark? =
    channelProvider
      .provideMediaChannel()
      .createBookmark(
        CreateBookmarkRequest(
          title = buildBookmarkTitle(currentChapter, chapterTime),
          time = totalTime.toInt(),
          libraryItemId = libraryItemId,
        ),
      ).fold(
        onSuccess = {
          val current = _memCache[libraryItemId] ?: emptyList()
          val updated = current + it
          _memCache[libraryItemId] = updated
          
          it
        },
        onFailure = { null },
      )
  
  suspend fun dropBookmark(bookmark: Bookmark) =
    channelProvider
      .provideMediaChannel()
      .dropBookmark(bookmark)
      .also {
        _memCache[bookmark.libraryItemId]
          ?.let { _memCache[bookmark.libraryItemId] = it.filterNot { it.isSame(bookmark) } }
        
      }
}
