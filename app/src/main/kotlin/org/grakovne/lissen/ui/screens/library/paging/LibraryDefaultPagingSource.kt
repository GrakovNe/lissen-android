package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingState
import org.grakovne.lissen.common.LibraryPagingException
import org.grakovne.lissen.common.LibraryPagingSource
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.domain.stableKey
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.util.concurrent.ConcurrentHashMap

class LibraryDefaultPagingSource(
  private val preferences: LissenSharedPreferences,
  private val mediaChannel: LissenMediaProvider,
  onTotalCountChanged: (Int) -> Unit,
) : LibraryPagingSource<LibraryEntry>(onTotalCountChanged) {
  private val seenKeys = ConcurrentHashMap.newKeySet<String>()

  override fun getRefreshKey(state: PagingState<Int, LibraryEntry>) =
    state
      .anchorPosition
      ?.let { anchorPosition ->
        state
          .closestPageToPosition(anchorPosition)
          ?.prevKey
          ?.plus(1)
          ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
      }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LibraryEntry> {
    val libraryId =
      preferences
        .getPreferredLibrary()
        ?.id
        ?: return LoadResult.Page(emptyList(), null, null)

    return mediaChannel
      .fetchLibrary(
        libraryId = libraryId,
        pageSize = params.loadSize,
        pageNumber = params.key ?: 0,
      ).fold(
        onSuccess = { result ->
          val nextPage = if (result.items.isEmpty()) null else result.currentPage + 1
          val prevKey = if (result.currentPage == 0) null else result.currentPage - 1

          onTotalCountChanged.invoke(result.totalItems)

          LoadResult.Page(
            data = result.items.filter { seenKeys.add(it.stableKey()) },
            prevKey = prevKey,
            nextKey = nextPage,
          )
        },
        onFailure = {
          LoadResult.Error(LibraryPagingException(it.code, it.message))
        },
      )
  }
}
