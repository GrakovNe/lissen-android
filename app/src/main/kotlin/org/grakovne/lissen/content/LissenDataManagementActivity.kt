package org.grakovne.lissen.content

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import org.grakovne.lissen.ui.activity.AppActivity
import org.grakovne.lissen.ui.navigation.SHOW_DOWNLOADS

@AndroidEntryPoint
class LissenDataManagementActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val intent =
      Intent(this, AppActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TASK
        action = SHOW_DOWNLOADS
      }

    startActivity(intent)
    finish()
  }
}
