package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.grakovne.lissen.domain.Book

class LibraryEmptyPagingSource() : PagingSource<Int, Book>() {

    override fun getRefreshKey(state: PagingState<Int, Book>) = null

    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, Book> = LoadResult.Page(emptyList(), null, null)
}
