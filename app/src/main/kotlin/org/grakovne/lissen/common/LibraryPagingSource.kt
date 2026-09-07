package org.grakovne.lissen.common

import androidx.paging.PagingSource
import org.grakovne.lissen.channel.common.OperationError

abstract class LibraryPagingSource<T : Any>(
  protected val onTotalCountChanged: (Int) -> Unit,
) : PagingSource<Int, T>()

class LibraryPagingException(
  val code: OperationError,
  message: String?,
) : Exception(message ?: code.toString())
