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

    private fun exchangeCodeForToken(code: String) {
        // Допустим, ваши параметры:
        val host = "https://audiobook.grakovne.org"

        // Собираем URL:
        val callbackUrl = Uri.parse("$host/auth/openid/callback").buildUpon()
            .appendQueryParameter("state", "42")
            .appendQueryParameter("code", code)
            .appendQueryParameter("code_verifier", preferences.veririer)
            .build()

        // Добавим недостающие куки вручную
        val requiredCookies = listOf(
            "auth_cb=audiobookshelf://oauth",
            "auth_method=openid"
        )

        val allCookies = (preferences.cookie.split(";") + requiredCookies)
            .map { it.trim() }
            .distinct() // на случай дубликатов
            .joinToString("; ")

        // Используем OkHttp (простейший пример без CookieJar)
        val client = OkHttpClient()
        val requestBuilder = Request.Builder()
            .url(callbackUrl.toString())
            .get()

        requestBuilder.addHeader("Cookie", allCookies)
        val request = requestBuilder.build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OAuth", "Callback request failed: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->

                    val location = r.header("Location")
                    val uri = Uri.parse(location)
                    val token = uri.getQueryParameter("setToken")
                    val state = uri.getQueryParameter("state")
                    Log.d("OAuth", "Got token from redirect: $token, state: $state")


                    val bodyString = r.body?.string().orEmpty()
                    Log.d("OAuth", "Callback response: $bodyString")

                    try {
                        val json = JSONObject(bodyString)
                        val user = json.getJSONObject("user")
                        val token = user.getString("token")
                        Log.d("OAuth", "Got token from callback: $token")

                        // Здесь сохраните токен в SharedPreferences
                        // или сразу во ViewModel:
                        // preferences.saveToken(token)
                        // И переходите на нужный экран.
                    } catch (e: JSONException) {
                        Log.e("OAuth", "JSON parse error: $e")
                    }
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
            // Если не по deep link (то есть первый запуск и пользователь ещё не авторизован):
            //   отправляем на OAuth
            // Если уже с deep link (callback), значит ничего не делаем,
            //   чтобы не открывать браузер снова
            if (currentUri == null || currentUri.scheme != "audiobookshelf") {

                //val (verifier, challenge, state) = generatePkce()

                preferences.veririer = "cac4a0beb116d848c3d1cc2bf2e448b6a0bfa4516811fb83b17c3808"
                preferences.challenge = "n-0HXHmY0zd-YNKGUkBthlToqCXM96A6XL_oxCewaHw"

                val url = Uri.parse("https://audiobook.grakovne.org/auth/openid/").buildUpon()
                    .appendQueryParameter("code_challenge", preferences.challenge)
                    .appendQueryParameter("code_challenge_method", "S256")
                    .appendQueryParameter("redirect_uri", "audiobookshelf://oauth")
                    .appendQueryParameter("client_id", "Audiobookshelf-App")
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("state", "42")
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

                            val setCookie = r.header("Set-Cookie")
                            preferences.cookie = setCookie!!
                            Log.d("OAuth", "Set-Cookie: $setCookie")

                            val intent = Intent(Intent.ACTION_VIEW, location!!.toUri())
                            startActivity(intent)
                        }
                    }
                })


            }
        }
    }

    fun generateRandomHexString(byteCount: Int = 32): String {
        val array = ByteArray(byteCount)
        java.security.SecureRandom().nextBytes(array)
        return array.joinToString("") { "%02x".format(it) }
    }

    fun sha256(input: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.US_ASCII))
    }

    fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)
    }

    fun generatePkce(): Triple<String, String, String> {
        val verifier = generateRandomHexString(42) // 42 bytes → 84 hex chars
        val challenge = base64UrlEncode(sha256(verifier))
        val state = generateRandomHexString(42)
        return Triple(verifier, challenge, state)
    }

}
