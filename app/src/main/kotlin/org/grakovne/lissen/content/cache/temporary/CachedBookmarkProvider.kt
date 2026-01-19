package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.lib.domain.Bookmark
import org.grakovne.lissen.lib.domain.CreateBookmarkRequest
import org.grakovne.lissen.lib.domain.isSame

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor(
    private val channelProvider: AudiobookshelfChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
  ) {
    suspend fun provideBookmarks(libraryItemId: String): List<Bookmark> =
      localCacheRepository
        .fetchBookmarks(libraryItemId)
        .fold(onSuccess = { it }, onFailure = { emptyList() })

    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val local =
        localCacheRepository
          .fetchBookmarks(libraryItemId)
          .fold(onSuccess = { it }, onFailure = { emptyList() })

      val remote =
        channelProvider
          .provideMediaChannel()
          .fetchBookmarks(libraryItemId)
          .fold(onSuccess = { it }, onFailure = { emptyList() })

      remote.forEach { localCacheRepository.upsertBookmark(it) }

      return (remote + local)
        .sortedByDescending { it.createdAt }
        .fold(mutableListOf<Bookmark>()) { acc, b ->
          if (acc.none { it.isSame(b) }) acc.add(b)
          acc
        }
    }

    suspend fun createBookmark(
      chapterTime: Double,
      totalTime: Double,
      libraryItemId: String,
      currentChapter: String,
    ): Bookmark? {
      val localDraft =
        Bookmark(
          libraryItemId = libraryItemId,
          title = buildBookmarkTitle(currentChapter, chapterTime),
          totalPosition = totalTime,
          createdAt = System.currentTimeMillis(),
        )

      return channelProvider
        .provideMediaChannel()
        .createBookmark(
          CreateBookmarkRequest(
            title = localDraft.title,
            time = totalTime.toInt(),
            libraryItemId = libraryItemId,
          ),
        ).foldAsync(
          onSuccess = { remote ->
            localCacheRepository.upsertBookmark(remote)
            remote
          },
          onFailure = {
            localCacheRepository.deleteBookmark(libraryItemId, totalTime)
            null
          },
        )
    }

    suspend fun dropBookmark(bookmark: Bookmark) {
      localCacheRepository.deleteBookmark(bookmark.libraryItemId, bookmark.totalPosition)

      channelProvider
        .provideMediaChannel()
        .dropBookmark(bookmark)
        .foldAsync(
          onSuccess = { Unit },
          onFailure = {
            localCacheRepository.deleteBookmark(bookmark.libraryItemId, bookmark.totalPosition)
            localCacheRepository.upsertBookmark(bookmark)
          },
        )
    }
  }
