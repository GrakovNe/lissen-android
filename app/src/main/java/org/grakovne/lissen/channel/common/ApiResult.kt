package org.grakovne.lissen.channel.common

import androidx.annotation.Keep

@Keep
sealed class ApiResult<T> {
  data class Success<T>(
    val data: T,
  ) : ApiResult<T>()

  data class Error<T>(
    val code: ApiError,
    val message: String? = null,
  ) : ApiResult<T>()

  fun <R> fold(
    onSuccess: (T) -> R,
    onFailure: (Error<T>) -> R,
  ): R =
    when (this) {
      is Success -> onSuccess(this.data)
      is Error -> onFailure(this)
    }

  suspend fun <R> foldAsync(
    onSuccess: suspend (T) -> R,
    onFailure: suspend (Error<T>) -> R,
  ): R =
    when (this) {
      is Success -> onSuccess(this.data)
      is Error -> onFailure(this)
    }

  suspend fun <R> map(transform: suspend (T) -> R): ApiResult<R> =
    when (this) {
      is Success -> Success(transform(this.data))
      is Error -> Error(this.code, this.message)
    }

  suspend fun <R> flatMap(transform: suspend (T) -> ApiResult<R>): ApiResult<R> =
    when (this) {
      is Success -> transform(this.data)
      is Error -> Error(this.code, this.message)
    }
}
