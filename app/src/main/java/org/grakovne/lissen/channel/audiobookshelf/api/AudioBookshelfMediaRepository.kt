package org.grakovne.lissen.channel.audiobookshelf.api

import okhttp3.ResponseBody
import org.grakovne.lissen.channel.audiobookshelf.client.AudiobookshelfMediaClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.BinaryApiClient
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfMediaRepository @Inject constructor(
    private val preferences: LissenSharedPreferences
) {

    suspend fun fetchBookCover(itemId: String): ApiResult<InputStream> =
        safeApiCall { getClientInstance().getItemCover(itemId) }

    private suspend fun safeApiCall(
        apiCall: suspend () -> Response<ResponseBody>
    ): ApiResult<InputStream> {
        return try {
            val response = apiCall.invoke()

            return when (response.code()) {
                200 -> when (val body = response.body()) {
                    null -> ApiResult.Error(ApiError.InternalError)
                    else -> ApiResult.Success(body.byteStream())
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
            println(e)
            ApiResult.Error(ApiError.InternalError)
        }
    }

    private fun getClientInstance(): AudiobookshelfMediaClient {
        val host = preferences.getHost()
        val token = preferences.getToken()

        if (host.isNullOrBlank() || token.isNullOrBlank()) {
            throw IllegalStateException("Host or token is missing")
        }

        return apiClient(host, token)
            .retrofit
            .create(AudiobookshelfMediaClient::class.java)
    }

    private fun apiClient(
        host: String,
        token: String
    ): BinaryApiClient = BinaryApiClient(
        host = host,
        token = token,
        customHeaders = preferences.getCustomHeaders()
    )
}
