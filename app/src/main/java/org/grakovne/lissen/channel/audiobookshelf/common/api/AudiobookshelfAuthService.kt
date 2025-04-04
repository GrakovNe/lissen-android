package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.CredentialsLoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.OAuthContextCache
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.randomPkce
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.common.createOkHttpClient
import org.grakovne.lissen.domain.UserAccount
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginResponseConverter: LoginResponseConverter,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val contextCache: OAuthContextCache
) : ChannelAuthService {

    override suspend fun authorize(
        host: String,
        username: String,
        password: String,
    ): ApiResult<UserAccount> {
        if (host.isBlank() || !urlPattern.matches(host)) {
            return ApiResult.Error(ApiError.InvalidCredentialsHost)
        }

        lateinit var apiService: AudiobookshelfApiClient

        try {
            val apiClient = ApiClient(
                host = host,
                requestHeaders = requestHeadersProvider.fetchRequestHeaders(),
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
                onFailure = { ApiResult.Error(it.code) },
            )
    }

    override suspend fun startOAuth(host: String) {
        Log.d(TAG, "Starting OAuth flow for $host")

        val client = createOkHttpClient()
            .newBuilder()
            .followRedirects(false)
            .build()

        val pkce = randomPkce()
        contextCache.storePkce(pkce)

        val url = host
            .toUri()
            .buildUpon()
            .appendEncodedPath("auth/openid/")
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("redirect_uri", "audiobookshelf://oauth")
            .appendQueryParameter("client_id", "Audiobookshelf-App")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", pkce.state)
            .build()

        val request = Request
            .Builder()
            .url(url.toString())
            .get()
            .build()

        client
            .newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed OAuth flow due to: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { redirect ->
                        Log.d(TAG, "Got Redirect from ABS")

                        val location = redirect.header("Location")
                        val cookieHeaders: List<String> = redirect.headers("Set-Cookie")

                        contextCache.storeCookies(cookieHeaders)

                        val intent = Intent(Intent.ACTION_VIEW, location!!.toUri()).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            })
    }

    private companion object {

        private val TAG = "AudiobookshelfAuthService"
        val urlPattern = Regex("^(http|https)://.*\$")
    }
}
