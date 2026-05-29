package org.grakovne.lissen.common

import android.content.Context
import android.content.Intent
import org.grakovne.lissen.ui.activity.AppActivity

fun Context.restartApplication() {
  val intent =
    Intent(this, AppActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

  startActivity(intent)
  Runtime.getRuntime().exit(0)
}
