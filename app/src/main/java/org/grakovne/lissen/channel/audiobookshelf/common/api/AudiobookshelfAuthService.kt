package org.grakovne.lissen.channel.audiobookshelf.common.api

import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.CredentialsLoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.AuthType
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.domain.UserAccount
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfAuthService @Inject constructor(
    private val loginResponseConverter: LoginResponseConverter,
    private val requestHeadersProvider: RequestHeadersProvider
) : ChannelAuthService {

    override suspend fun authorize(
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

        val response: ApiResult<LoggedUserResponse> =
            safeApiCall { apiService.login(CredentialsLoginRequest(username, password)) }

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

    override fun getAuthType(): AuthType = AuthType.CREDENTIALS

    private companion object {

        val urlPattern = Regex("^(http|https)://.*\$")
    }
}
