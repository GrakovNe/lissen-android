package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import retrofit2.Response
import java.io.IOException

suspend fun <T> safeApiCall(
    apiCall: suspend () -> Response<T>
): ApiResult<T> {
    return try {
        val response = apiCall.invoke()

        return when (response.code()) {
            200 -> when (val body = response.body()) {
                null -> ApiResult.Error(ApiError.InternalError)
                else -> ApiResult.Success(body)
            }

            400 -> ApiResult.Error(ApiError.InternalError)
            401 -> ApiResult.Error(ApiError.Unauthorized)
            403 -> ApiResult.Error(ApiError.Unauthorized)
            404 -> ApiResult.Error(ApiError.InternalError)
            500 -> ApiResult.Error(ApiError.InternalError)
            else -> ApiResult.Error(ApiError.InternalError)
        }
    } catch (e: IOException) {
        ApiResult.Error(ApiError.NetworkError)
    } catch (e: Exception) {
        ApiResult.Error(ApiError.InternalError)
    }
}
