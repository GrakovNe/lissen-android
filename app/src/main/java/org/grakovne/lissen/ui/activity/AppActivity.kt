package org.grakovne.lissen.ui.activity

import android.app.ComponentCaller
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.common.NetworkQualityService
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.navigation.AppNavHost
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.theme.LissenTheme
import javax.inject.Inject
import androidx.core.net.toUri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.OAuthContextCache
import org.grakovne.lissen.channel.audiobookshelf.common.oauth.randomPkce
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

@AndroidEntryPoint
class AppActivity : ComponentActivity() {

    @Inject
    lateinit var preferences: LissenSharedPreferences

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var networkQualityService: NetworkQualityService

    @Inject
    lateinit var sharedPreferences: LissenSharedPreferences

    @Inject
    lateinit var contextCache: OAuthContextCache

    private fun exchangeCodeForToken(code: String) {
        // Допустим, ваши параметры:
        val host = "https://audiobook.grakovne.org"

        val pkce = contextCache.readPkce()

        // Собираем URL:
        val callbackUrl = Uri.parse("$host/auth/openid/callback").buildUpon()
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("code", code)
            .appendQueryParameter("code_verifier", pkce.verifier)
            .build()

        // Используем OkHttp (простейший пример без CookieJar)
        val client = OkHttpClient()
        val requestBuilder = Request.Builder()
            .url(callbackUrl.toString())
            .get()

        requestBuilder.addHeader("Cookie", preferences.cookie)
        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OAuth", "Callback request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    Log.d("OAuth", r.body.toString())
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null && data.scheme == "audiobookshelf") {
            val token = data.getQueryParameter("code") ?: ""
            Log.d("OAuth", "Got token: $token")
            exchangeCodeForToken(token)
        }

        setContent {
            val colorScheme by sharedPreferences
                .colorSchemeFlow
                .collectAsState(initial = sharedPreferences.getColorScheme())

            LissenTheme(colorScheme) {
                val navController = rememberNavController()

                AppNavHost(
                    navController = navController,
                    navigationService = AppNavigationService(navController),
                    preferences = preferences,
                    imageLoader = imageLoader,
                    networkQualityService = networkQualityService,
                )
            }

            val currentUri = intent?.data
            if (currentUri == null || currentUri.scheme != "audiobookshelf") {

                val (verifier, challenge, state) = randomPkce()

                preferences.veririer = verifier
                preferences.challenge = challenge
                preferences.state = state

                val url = Uri.parse("https://audiobook.grakovne.org/auth/openid/").buildUpon()
                    .appendQueryParameter("code_challenge", preferences.challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("redirect_uri", "audiobookshelf://oauth")
                    .appendQueryParameter("client_id", "Audiobookshelf-App")
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("state", state)
                    .build()

                val client = OkHttpClient.Builder()
                    .followRedirects(false)
                    .build()

                val request = Request.Builder()
                    .url(url.toString())
                    .get()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("OAuth", "Failed: $e")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { r ->
                            val location = r.header("Location")
                            Log.d("OAuth", "Location: $location")

                            val setCookieHeaders: List<String> = response.headers("Set-Cookie")

                            val cookie = setCookieHeaders
                                .map { it.substringBefore(";") } // оставляем только "ключ=значение"
                                .joinToString("; ")

                            preferences.cookie = cookie
                            Log.d("OAuth", "Set-Cookie: $cookie")

                            val intent = Intent(Intent.ACTION_VIEW, location!!.toUri())
                            //startActivity(intent)
                        }
                    }
                })


            }
        }
    }

}
