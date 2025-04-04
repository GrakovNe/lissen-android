package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.CredentialsLoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.ApiError
import org.grakovne.lissen.channel.common.ApiResult
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.OAuthContextCache
import org.grakovne.lissen.channel.common.randomPkce
import org.grakovne.lissen.common.createOkHttpClient
import org.grakovne.lissen.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loginResponseConverter: LoginResponseConverter,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val preferences: LissenSharedPreferences,
    private val contextCache: OAuthContextCache
) : ChannelAuthService(preferences) {

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

    override suspend fun exchangeToken(
        host: String,
        code: String
    ) {
        val pkce = contextCache.readPkce()
        val cookie = contextCache.readCookies()

        contextCache.clearPkce()
        contextCache.clearCookies()

        val callbackUrl = host
            .toUri()
            .buildUpon()
            .appendEncodedPath("auth/openid/callback")
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("code", code)
            .appendQueryParameter("code_verifier", pkce.verifier)
            .build()

        val client = createOkHttpClient()

        val request = Request
            .Builder()
            .url(callbackUrl.toString())
            .addHeader("Cookie", cookie)
            .build()

        client
            .newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Callback request failed: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    val raw = response.body?.string() ?: return

                    val user = try {
                        Gson().fromJson(raw, LoggedUserResponse::class.java)
                    } catch (ex: Exception) {
                        Log.e(TAG, "Unable to get User data from response: $ex")
                        return
                    }

                    persistCredentials(
                        host = host,
                        username = user.user.username,
                        token = user.user.token
                    )
                }
            })
    }

    private companion object {

        private val TAG = "AudiobookshelfAuthService"
        val urlPattern = Regex("^(http|https)://.*\$")
    }
}
