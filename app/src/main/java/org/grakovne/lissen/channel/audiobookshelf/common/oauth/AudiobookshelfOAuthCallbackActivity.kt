package org.grakovne.lissen.channel.audiobookshelf.common.oauth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.common.OAuthContextCache
import javax.inject.Inject

@AndroidEntryPoint
class AudiobookshelfOAuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var contextCache: OAuthContextCache

    @Inject
    lateinit var authService: AudiobookshelfAuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data = intent?.data

        if (null == data) {
            finish()
        }

        if (intent?.action == Intent.ACTION_VIEW && data != null && data.scheme == "audiobookshelf") {
            val code = data.getQueryParameter("code") ?: ""
            Log.d(TAG, "Got Exchange code from ABS")

            lifecycleScope.launch {
                authService.exchangeToken("https://audiobook.grakovne.org", code)
            }
        }
    }

    companion object {

        private const val TAG = "AudiobookshelfOAuthCallbackActivity"
    }
}