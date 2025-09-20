package org.grakovne.lissen.ui.screens.settings.advanced.cache

import androidx.paging.PagingSource
import androidx.paging.PagingState
import org.grakovne.lissen.content.cache.persistent.LocalCacheRepository
import org.grakovne.lissen.lib.domain.DetailedItem

class CachedItemsPageSource(
  private val localCacheRepository: LocalCacheRepository,
) : PagingSource<Int, DetailedItem>() {
  override fun getRefreshKey(state: PagingState<Int, DetailedItem>): Int? =
    state
      .anchorPosition
      ?.let { anchorPosition ->
        state
          .closestPageToPosition(anchorPosition)
          ?.prevKey
          ?.plus(1)
          ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
      }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DetailedItem> =
    localCacheRepository
      .fetchDetailedItems(
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
