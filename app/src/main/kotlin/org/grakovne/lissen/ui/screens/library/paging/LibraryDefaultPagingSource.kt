package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingState
import org.grakovne.lissen.common.LibraryPagingSource
import org.grakovne.lissen.content.BookRepository
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

class LibraryDefaultPagingSource(
  private val preferences: LissenSharedPreferences,
  private val bookRepository: BookRepository,
  private val downloadedOnly: Boolean,
  onTotalCountChanged: (Int) -> Unit,
) : LibraryPagingSource<Book>(onTotalCountChanged) {
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

    return bookRepository
      .fetchBooks(
        libraryId = libraryId,
        pageSize = params.loadSize,
        pageNumber = params.key ?: 0,
        downloadedOnly = downloadedOnly,
      ).fold(
        onSuccess = { result ->
          val nextPage = if (result.items.isEmpty()) null else result.currentPage + 1
          val prevKey = if (result.currentPage == 0) null else result.currentPage - 1

          onTotalCountChanged.invoke(result.totalItems)

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
