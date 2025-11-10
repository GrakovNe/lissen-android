package org.grakovne.lissen.common

import androidx.paging.PagingSource

abstract class LibraryPagingSource<T : Any>(
  protected val onTotalCountChanged: (Int) -> Unit,
) : PagingSource<Int, T>()
