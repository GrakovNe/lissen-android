package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

class LibraryDefaultPagingSource(
  private val preferences: LissenSharedPreferences,
  private val mediaChannel: LissenMediaProvider,
) : PagingSource<Int, Book>() {
  override fun getRefreshKey(state: PagingState<Int, Book>) =
    state
      .anchorPosition
      ?.let { anchorPosition ->
        state
          .closestPageToPosition(anchorPosition)
          ?.prevKey
          ?.plus(1)
          ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
      }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
    val libraryId =
      preferences
        .getPreferredLibrary()
        ?.id
        ?: return LoadResult.Page(emptyList(), null, null)

    return mediaChannel
      .fetchBooks(
        libraryId = libraryId,
        pageSize = params.loadSize,
        pageNumber = params.key ?: 0,
      ).fold(
        onSuccess = { result ->
          val nextPage = if (result.items.isEmpty()) null else result.currentPage + 1
          val prevKey = if (result.currentPage == 0) null else result.currentPage - 1

          LoadResult.Page(
            data = result.items,
            prevKey = prevKey,
            nextKey = nextPage,
          )
        },
        onFailure = {
          LoadResult.Page(emptyList(), null, null)
        },
      )
  }
}
