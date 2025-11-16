package org.grakovne.lissen.channel.audiobookshelf.common.api

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.core.net.toUri
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.grakovne.lissen.channel.audiobookshelf.common.client.AudiobookshelfApiClient
import org.grakovne.lissen.channel.audiobookshelf.common.converter.AuthMethodResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.converter.LoginResponseConverter
import org.grakovne.lissen.channel.audiobookshelf.common.model.auth.AuthMethodResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.connection.PingResponse
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.CredentialsLoginRequest
import org.grakovne.lissen.channel.audiobookshelf.common.model.user.LoggedUserResponse
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.AuthClient
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.AuthHost
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.AuthScheme
import org.grakovne.lissen.channel.common.ApiClient
import org.grakovne.lissen.channel.common.AuthData
import org.grakovne.lissen.channel.common.AuthData.Companion.empty
import org.grakovne.lissen.channel.common.AuthMethod
import org.grakovne.lissen.channel.common.ChannelAuthService
import org.grakovne.lissen.channel.common.OAuthContextCache
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.channel.common.randomPkce
import org.grakovne.lissen.common.moshi
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfAuthService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val loginResponseConverter: LoginResponseConverter,
    private val requestHeadersProvider: RequestHeadersProvider,
    private val preferences: LissenSharedPreferences,
    private val contextCache: OAuthContextCache,
    private val authMethodResponseConverter: AuthMethodResponseConverter,
  ) : ChannelAuthService(preferences) {
    private val client =
      createOkHttpClient(requestHeaders = preferences.getCustomHeaders(), preferences = preferences)
        .newBuilder()
        .followRedirects(false)
        .build()

    private suspend fun suggestHost(input: String): String {
      if (PROTOCOL_SCHEMES.any { input.startsWith(it) } || input.contains("://")) {
        return input
      }

      val suggestedHost =
        PROTOCOL_SCHEMES
          .firstNotNullOfOrNull { scheme ->
            try {
              val baseUrl =
                HttpUrl
                  .Builder()
                  .scheme(scheme.removeSuffix("://"))
                  .host(input)
                  .build()

              val client = createApiClient(baseUrl.toString())
              val response = client.ping()

              when (response.isSuccessful && response.body()?.success == true) {
                true -> {
                  val url = response.raw().request.url

                  when (url.port == HttpUrl.defaultPort(url.scheme)) {
                    true ->
                      HttpUrl
                        .Builder()
                        .scheme(url.scheme)
                        .host(url.host)
                        .build()
                        .toString()

                    false ->
                      HttpUrl
                        .Builder()
                        .scheme(url.scheme)
                        .host(url.host)
                        .port(url.port)
                        .build()
                        .toString()
                  }
                }

                false -> null
              }
            } catch (_: Exception) {
              null
            }
          }

      return suggestedHost ?: input
    }

    override suspend fun authorize(
      input: String,
      username: String,
      password: String,
      onSuccess: suspend (UserAccount) -> Unit,
    ): OperationResult<UserAccount> {
      if (input.isBlank()) {
        return OperationResult.Error(OperationError.InvalidCredentialsHost)
      }

      val host = suggestHost(input)

      lateinit var apiService: AudiobookshelfApiClient

      try {
        apiService = createApiClient(host)
      } catch (e: Exception) {
        return OperationResult.Error(OperationError.InternalError)
      }

      val response: OperationResult<LoggedUserResponse> =
        safeApiCall { apiService.login(CredentialsLoginRequest(username, password)) }

      return response
        .foldAsync(
          onSuccess = {
            loginResponseConverter
              .apply(host, it)
              .also { onSuccess(it) }
              .let { OperationResult.Success(it) }
          },
          onFailure = { OperationResult.Error(it.code) },
        )
    }

    override suspend fun fetchAuthMethods(input: String): OperationResult<AuthData> {
      return withContext(Dispatchers.IO) {
        try {
          val host = suggestHost(input)
          
          val url =
            host
              .toUri()
              .buildUpon()
              .appendEncodedPath("status")
              .build()

          val client = createOkHttpClient(requestHeaders = preferences.getCustomHeaders(), preferences = preferences)
          val request =
            Request
              .Builder()
              .url(url.toString())
              .get()
              .build()
          val response = client.newCall(request).execute()

          if (!response.isSuccessful) {
            return@withContext OperationResult.Success(empty)
          }

          val body = response.body.string()

          val authMethod =
            moshi
              .adapter(AuthMethodResponse::class.java)
              .fromJson(body)
              ?: return@withContext OperationResult.Success(empty)

          val converted = authMethodResponseConverter.apply(authMethod)
          OperationResult.Success(converted)
        } catch (e: Exception) {
          OperationResult.Success(empty)
        }
      }
    }

    override suspend fun startOAuth(
      input: String,
      onSuccess: () -> Unit,
      onFailure: (OperationError) -> Unit,
    ) {
      val host = suggestHost(input)
      Timber.d("Starting OAuth flow for $host")

      preferences.saveHost(host)

      val pkce = randomPkce()
      contextCache.storePkce(pkce)

      val url =
        host
          .toUri()
          .buildUpon()
          .appendEncodedPath("auth/openid/")
          .appendQueryParameter("code_challenge", pkce.challenge)
          .appendQueryParameter("code_challenge_method", "S256")
          .appendQueryParameter("redirect_uri", "$AuthScheme://$AuthHost")
          .appendQueryParameter("client_id", AuthClient)
          .appendQueryParameter("response_type", "code")
          .appendQueryParameter("state", pkce.state)
          .build()

      val request =
        Request
          .Builder()
          .url(url.toString())
          .get()
          .build()

      client
        .newCall(request)
        .enqueue(
          object : Callback {
            override fun onFailure(
              call: Call,
              e: IOException,
            ) {
              Timber.e("Failed OAuth flow due to: $e")
              onFailure(examineError(e.message ?: ""))
            }

            override fun onResponse(
              call: Call,
              response: Response,
            ) {
              Timber.d("Got Redirect from ABS")

              if (response.code != 302) {
                onFailure(examineError(response.body.string()))
                return
              }

              val location =
                response
                  .header("Location")
                  ?: run {
                    onFailure(examineError("invalid_redirect"))
                    return
                  }

              try {
                val cookieHeaders: List<String> = response.headers("Set-Cookie")
                contextCache.storeCookies(cookieHeaders)

                onSuccess()
                forwardAuthRequest(location)
              } catch (ex: Exception) {
                onFailure(examineError(ex.message ?: ""))
              }
            }
          },
        )
    }

    fun forwardAuthRequest(url: String) {
      val customTabsIntent = CustomTabsIntent.Builder().build()
      customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
      customTabsIntent.launchUrl(context, url.toUri())
    }

    override suspend fun exchangeToken(
      input: String,
      code: String,
      onSuccess: suspend (UserAccount) -> Unit,
      onFailure: (String) -> Unit,
    ) {
      val host = suggestHost(input)
      val pkce = contextCache.readPkce()
      val cookie = contextCache.readCookies()

      contextCache.clearPkce()
      contextCache.clearCookies()

      val callbackUrl =
        host
          .toUri()
          .buildUpon()
          .appendEncodedPath("auth/openid/callback")
          .appendQueryParameter("state", pkce.state)
          .appendQueryParameter("code", code)
          .appendQueryParameter("code_verifier", pkce.verifier)
          .build()

      val client = createOkHttpClient(requestHeaders = preferences.getCustomHeaders(), preferences = preferences)

      val request =
        Request
          .Builder()
          .url(callbackUrl.toString())
          .addHeader("Cookie", cookie)
          .build()

      client
        .newCall(request)
        .enqueue(
          object : Callback {
            override fun onFailure(
              call: Call,
              e: IOException,
            ) {
              Timber.e("Callback request failed: $e")
              onFailure(e.message ?: "")
            }

            override fun onResponse(
              call: Call,
              response: Response,
            ) {
              val raw =
                response
                  .body
                  .string()

              val user =
                try {
                  moshi
                    .adapter(LoggedUserResponse::class.java)
                    .fromJson(raw)
                    ?.let { loginResponseConverter.apply(host, it) }
                    ?: return
                } catch (ex: Exception) {
                  Timber.e("Unable to get User data from response: $ex")
                  onFailure(ex.message ?: "")
                  return
                }

              CoroutineScope(Dispatchers.IO).launch { onSuccess(user) }
            }
          },
        )
    }

    private fun createApiClient(host: String): AudiobookshelfApiClient {
      val client =
        ApiClient(
          host = host,
          preferences = preferences,
          requestHeaders = requestHeadersProvider.fetchRequestHeaders(),
        )

      return client.retrofit.create(AudiobookshelfApiClient::class.java)
    }

    private companion object {
      private val PROTOCOL_SCHEMES = listOf("https://", "http://")
    }
  }
