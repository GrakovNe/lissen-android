package org.grakovne.lissen.ui.screens.library.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book

class LibraryDefaultPagingSource(
    private val libraryId: String,
    private val mediaChannel: LissenMediaProvider
) : PagingSource<Int, Book>() {

    override fun getRefreshKey(state: PagingState<Int, Book>) = state
        .anchorPosition
        ?.let { anchorPosition ->
            state
                .closestPageToPosition(anchorPosition)
                ?.prevKey
                ?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        return mediaChannel
            .fetchBooks(
                libraryId = libraryId,
                pageSize = params.loadSize,
                pageNumber = params.key ?: 0
            )
            .fold(
                onSuccess = { result ->
                    val nextPage = if (result.items.isEmpty()) null else result.currentPage + 1
                    val prevKey = if (result.currentPage == 0) null else result.currentPage - 1

                    LoadResult.Page(
                        data = result.items,
                        prevKey = prevKey,
                        nextKey = nextPage
                    )
                },
                onFailure = {
                LoadResult.Page(emptyList(), null, null)
                }
            )
    }
}
