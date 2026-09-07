package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingState
import org.grakovne.lissen.common.LibraryPagingException
import org.grakovne.lissen.common.LibraryPagingSource
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.LibraryEntry
import org.grakovne.lissen.persistence.preferences.LibraryPreferences

class LibrarySearchPagingSource(
  private val preferences: LibraryPreferences,
  private val mediaChannel: LissenMediaProvider,
  private val searchToken: String,
  private val limit: Int,
  onTotalCountChanged: (Int) -> Unit,
) : LibraryPagingSource<LibraryEntry>(onTotalCountChanged) {
  override fun getRefreshKey(state: PagingState<Int, LibraryEntry>) = null

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LibraryEntry> {
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
        onSuccess = { books ->
          onTotalCountChanged.invoke(books.size)
          LoadResult.Page(books.map { LibraryEntry.BookEntry(it) }, null, null)
        },
        onFailure = { LoadResult.Error(LibraryPagingException(it.code, it.message)) },
      )
  }
}
