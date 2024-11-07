package org.grakovne.lissen.channel.audiobookshelf.api

import org.grakovne.lissen.channel.audiobookshelf.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.model.AuthorResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryItemIdResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryItemsResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibraryResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LibrarySearchResponse
import org.grakovne.lissen.channel.audiobookshelf.model.LoginRequest
import org.grakovne.lissen.channel.audiobookshelf.model.LoginResponse
import org.grakovne.lissen.channel.audiobookshelf.model.MediaProgressResponse
import org.grakovne.lissen.channel.audiobookshelf.model.PersonalizedFeedResponse
import org.grakovne.lissen.channel.audiobookshelf.model.PlaybackSessionResponse
import org.grakovne.lissen.channel.audiobookshelf.model.StartPlaybackRequest
import org.grakovne.lissen.channel.audiobookshelf.model.SyncProgressRequest
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioBookshelfDataRepository @Inject constructor(
    private val loginResponseConverter: LoginResponseConverter,
    private val preferences: LissenSharedPreferences,
    private val requestHeadersProvider: RequestHeadersProvider
) {

    private var configCache: ApiClientConfig? = null
    private var clientCache: AudiobookshelfApiClient? = null

    suspend fun fetchLibraries(): ApiResult<LibraryResponse> =
        safeApiCall { getClientInstance().fetchLibraries() }

    suspend fun fetchAuthorItems(
        authorId: String
    ): ApiResult<AuthorResponse> = safeApiCall {
        getClientInstance()
            .fetchAuthorLibraryItems(
                authorId = authorId
            )
    }

    suspend fun searchLibraryItems(
        libraryId: String,
        query: String,
        limit: Int
    ): ApiResult<LibrarySearchResponse> = safeApiCall {
        getClientInstance()
            .searchLibraryItems(
                libraryId = libraryId,
                request = query,
                limit = limit
            )
    }

    suspend fun fetchLibraryItems(
        libraryId: String,
        pageSize: Int,
        pageNumber: Int
    ): ApiResult<LibraryItemsResponse> =
        safeApiCall {
            getClientInstance()
                .fetchLibraryItems(
                    libraryId = libraryId,
                    pageSize = pageSize,
                    pageNumber = pageNumber
                )
        }

    suspend fun fetchLibraryItem(itemId: String): ApiResult<LibraryItemIdResponse> =
        safeApiCall { getClientInstance().fetchLibraryItem(itemId) }

    suspend fun fetchPersonalizedFeed(libraryId: String): ApiResult<List<PersonalizedFeedResponse>> =
        safeApiCall { getClientInstance().fetchPersonalizedFeed(libraryId) }

    suspend fun fetchLibraryItemProgress(itemId: String): ApiResult<MediaProgressResponse> =
        safeApiCall { getClientInstance().fetchLibraryItemProgress(itemId) }

    suspend fun startPlayback(
        itemId: String,
        request: StartPlaybackRequest
    ): ApiResult<PlaybackSessionResponse> =
        safeApiCall { getClientInstance().startPlayback(itemId, request) }

    suspend fun stopPlayback(sessionId: String): ApiResult<Unit> =
        safeApiCall { getClientInstance().stopPlayback(sessionId) }

    suspend fun publishLibraryItemProgress(
        itemId: String,
        progress: SyncProgressRequest
    ): ApiResult<Unit> =
        safeApiCall { getClientInstance().publishLibraryItemProgress(itemId, progress) }

    suspend fun authorize(
        host: String,
        username: String,
        password: String
    ): ApiResult<UserAccount> {
        if (host.isBlank() || !urlPattern.matches(host)) {
            return ApiResult.Error(ApiError.InvalidCredentialsHost)
        }

        lateinit var apiService: AudiobookshelfApiClient

        try {
            val apiClient = ApiClient(
                host = host,
                requestHeaders = requestHeadersProvider.fetchRequestHeaders()
            )

            apiService = apiClient.retrofit.create(AudiobookshelfApiClient::class.java)
        } catch (e: Exception) {
            return ApiResult.Error(ApiError.InternalError)
        }

        val response: ApiResult<LoginResponse> =
            safeApiCall { apiService.login(LoginRequest(username, password)) }

        return response
            .fold(
                onSuccess = {
                    loginResponseConverter
                        .apply(it)
                        .let { ApiResult.Success(it) }
                },
                onFailure = { ApiResult.Error(it.code) }
            )
    }

    private suspend fun <T> safeApiCall(
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

    private fun getClientInstance(): AudiobookshelfApiClient {
        val host = preferences.getHost()
        val token = preferences.getToken()

        val cache = ApiClientConfig(
            host = host,
            token = token,
            customHeaders = requestHeadersProvider.fetchRequestHeaders()
        )

        val currentClientCache = clientCache

        return when (currentClientCache == null || cache != configCache) {
            true -> {
                val instance = createClientInstance()
                configCache = cache
                clientCache = instance
                instance
            }

            else -> currentClientCache
        }
    }

    private fun createClientInstance(): AudiobookshelfApiClient {
        val host = preferences.getHost()
        val token = preferences.getToken()

        if (host.isNullOrBlank() || token.isNullOrBlank()) {
            throw IllegalStateException("Host or token is missing")
        }

        return apiClient(host, token)
            .retrofit
            .create(AudiobookshelfApiClient::class.java)
    }

    private fun apiClient(
        host: String,
        token: String?
    ): ApiClient = ApiClient(
        host = host,
        token = token,
        requestHeaders = requestHeadersProvider.fetchRequestHeaders()
    )

    companion object {

        val urlPattern = Regex("^(http|https)://.*\$")
    }
}
