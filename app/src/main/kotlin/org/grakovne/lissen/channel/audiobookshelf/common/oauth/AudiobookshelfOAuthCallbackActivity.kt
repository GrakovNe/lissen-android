package org.grakovne.lissen.channel.audiobookshelf.common.oauth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudiobookshelfAuthService
import org.grakovne.lissen.channel.common.OAuthContextCache
import org.grakovne.lissen.channel.common.makeText
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.UserAccount
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.ui.activity.AppActivity
import javax.inject.Inject

@AndroidEntryPoint
class AudiobookshelfOAuthCallbackActivity : ComponentActivity() {
  @Inject
  lateinit var contextCache: OAuthContextCache

  @Inject
  lateinit var authService: AudiobookshelfAuthService

  @Inject
  lateinit var mediaProvider: LissenMediaProvider

  @Inject
  lateinit var preferences: LissenSharedPreferences

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val data = intent?.data

    if (null == data) {
      finish()
      return
    }

    if (intent?.action == Intent.ACTION_VIEW && data.scheme == AuthScheme) {
      val code = data.getQueryParameter("code") ?: ""
      Log.d(TAG, "Got Exchange code from ABS")

      lifecycleScope.launch {
        authService.exchangeToken(
          host =
            preferences.getHost() ?: kotlin.run {
              onLoginFailed("invalid_host")
              return@launch
            },
          code = code,
          onSuccess = { onLogged(it) },
          onFailure = { onLoginFailed(it) },
        )
      }
    }
  }

  private suspend fun onLogged(userAccount: UserAccount) {
    mediaProvider.onPostLogin(
      host = preferences.getHost() ?: return,
      account = userAccount,
    )

    val intent =
      Intent(this, AppActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TASK
      }

    startActivity(intent)
    finish()
  }

  private fun onLoginFailed(reason: String) {
    runOnUiThread {
      authService
        .examineError(reason)
        .makeText(this)
        .let { Toast.makeText(this, it, LENGTH_SHORT).show() }

      finish()
    }
  }

  companion object {
    private const val TAG = "AudiobookshelfOAuthCallbackActivity"
  }
}
