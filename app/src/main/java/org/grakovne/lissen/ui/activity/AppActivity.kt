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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data = intent?.data
        if (intent?.action == Intent.ACTION_VIEW && data != null && data.scheme == "myapp") {
            val token = data.getQueryParameter("setToken")
            Log.d("OAuth", "Got token: $token")
            // ... обработка
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
            if (currentUri == null || currentUri.scheme != "myapp") {
                LaunchedEffect(Unit) {
                    val oauthUrl = "https://audiobook.grakovne.org/auth/openid?callback=myapp://oauthcallback"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(oauthUrl))
                    startActivity(intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null && uri.scheme == "myapp") {
                val code = uri.getQueryParameter("code")
                val token = uri.getQueryParameter("token")

                Log.d("OAuth", "Got code: $code, or token: $token")

                // ... тут уже сохраняете token, делаете навигацию и т.п.
            }
        }
    }
}
