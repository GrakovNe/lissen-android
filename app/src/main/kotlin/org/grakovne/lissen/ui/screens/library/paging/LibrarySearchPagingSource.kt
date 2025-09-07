package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.Book
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences

class LibrarySearchPagingSource(
  private val preferences: LissenSharedPreferences,
  private val mediaChannel: LissenMediaProvider,
  private val searchToken: String,
  private val limit: Int,
) : PagingSource<Int, Book>() {
  override fun getRefreshKey(state: PagingState<Int, Book>) = null

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
    val libraryId =
      preferences
        .getPreferredLibrary()
        ?.id
        ?: return LoadResult.Page(emptyList(), null, null)

    if (searchToken.isBlank()) {
      return LoadResult.Page(emptyList(), null, null)
    }

    return mediaChannel
      .searchBooks(libraryId, searchToken, limit)
      .fold(
        onSuccess = { LoadResult.Page(it, null, null) },
        onFailure = { LoadResult.Page(emptyList(), null, null) },
      )
  }
}
