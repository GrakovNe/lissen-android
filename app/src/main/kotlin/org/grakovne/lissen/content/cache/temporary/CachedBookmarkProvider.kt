package org.grakovne.lissen.content.cache.temporary

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.BookmarkSyncState
import org.grakovne.lissen.domain.CreateBookmarkRequest
import org.grakovne.lissen.domain.isSame
import timber.log.Timber

@Singleton
class CachedBookmarkProvider
  @Inject
  constructor(
    private val channelProvider: AudiobookshelfChannelProvider,
    private val localCacheRepository: LocalCacheRepository,
  ) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun provideBookmarks(libraryItemId: String): List<Bookmark> =
      localCacheRepository
        .fetchBookmarks(libraryItemId)
        .filter { it.syncState != BookmarkSyncState.PENDING_DELETE }
        .sortedByDescending { it.createdAt }
        .fold(mutableListOf()) { acc, b ->
          if (acc.none { it.isSame(b) }) acc.add(b)
          acc
        }

    suspend fun fetchBookmarks(libraryItemId: String): List<Bookmark> {
      val local = localCacheRepository.fetchBookmarks(libraryItemId)

      local
        .asSequence()
        .filter { it.libraryItemId == libraryItemId }
        .filter { it.syncState == BookmarkSyncState.PENDING_CREATE }
        .forEach { pending ->
          channelProvider
            .provideMediaChannel()
            .createBookmark(
              CreateBookmarkRequest(
                title = pending.title,
                time = pending.totalPosition.toInt(),
                libraryItemId = pending.libraryItemId,
              ),
            ).foldAsync(
              onSuccess = { remoteCreated ->
                localCacheRepository.deleteBookmark(pending.libraryItemId, pending.totalPosition)
                localCacheRepository.upsertBookmark(remoteCreated.copy(syncState = BookmarkSyncState.SYNCED))
              },
              onFailure = {},
            )
        }

      local
        .asSequence()
        .filter { it.libraryItemId == libraryItemId }
        .filter { it.syncState == BookmarkSyncState.PENDING_DELETE }
        .forEach { pendingDelete ->
          Timber.d("Uploading pending bookmark delete for $libraryItemId at position=${pendingDelete.totalPosition.toInt()}s")
          channelProvider
            .provideMediaChannel()
            .dropBookmark(pendingDelete)
            .foldAsync(
              onSuccess = {
                localCacheRepository.deleteBookmark(pendingDelete.libraryItemId, pendingDelete.totalPosition)
              },
              onFailure = {},
            )
        }

      val remote =
        channelProvider
          .provideMediaChannel()
          .fetchBookmarks(libraryItemId)
          .foldAsync(
            onSuccess = { it },
            onFailure = { return@foldAsync null },
          ) ?: return provideBookmarks(libraryItemId)

      remote.forEach { localCacheRepository.upsertBookmark(it.copy(syncState = BookmarkSyncState.SYNCED)) }

      val afterSyncLocal = localCacheRepository.fetchBookmarks(libraryItemId)

      afterSyncLocal
        .asSequence()
        .filter { it.syncState == BookmarkSyncState.SYNCED }
        .filter { l -> remote.none { r -> r.isSame(l) } }
        .forEach { orphan -> localCacheRepository.deleteBookmark(orphan.libraryItemId, orphan.totalPosition) }

      return provideBookmarks(libraryItemId)
    }

    suspend fun createBookmark(
      totalTime: Double,
      libraryItemId: String,
      title: String,
    ): Bookmark {
      Timber.d("Creating bookmark (local-first) for $libraryItemId at position=${totalTime.toInt()}s")
      val localDraft =
        Bookmark(
          libraryItemId = libraryItemId,
          title = title,
          totalPosition = totalTime,
          createdAt = System.currentTimeMillis(),
          syncState = BookmarkSyncState.PENDING_CREATE,
        )

      localCacheRepository.upsertBookmark(localDraft)

      scope.launch {
        channelProvider
          .provideMediaChannel()
          .createBookmark(
            CreateBookmarkRequest(
              title = localDraft.title,
              time = totalTime.toInt(),
              libraryItemId = libraryItemId,
            ),
          ).foldAsync(
            onSuccess = { remote ->
              localCacheRepository.upsertBookmark(remote.copy(syncState = BookmarkSyncState.SYNCED))
            },
            onFailure = { /* keep localDraft as-is */ },
          )
      }

      return localDraft
    }

    suspend fun dropBookmark(bookmark: Bookmark) {
      Timber.d("Dropping bookmark (local-first) for ${bookmark.libraryItemId} at position=${bookmark.totalPosition.toInt()}s")
      localCacheRepository.upsertBookmark(
        bookmark.copy(syncState = BookmarkSyncState.PENDING_DELETE),
      )

      scope.launch {
        channelProvider
          .provideMediaChannel()
          .dropBookmark(bookmark)
          .foldAsync(
            onSuccess = { localCacheRepository.deleteBookmark(bookmark.libraryItemId, bookmark.totalPosition) },
            onFailure = { /* keep PENDING_DELETE for retry on reconnect */ },
          )
      }
    }
  }
