package org.grakovne.lissen.ui.effects

import android.app.Activity
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun WindowBlurEffect() {
  val view = LocalView.current
  if (VERSION.SDK_INT >= VERSION_CODES.S) {
    SideEffect {
      val activity = view.context as? Activity ?: return@SideEffect
      val window = activity.window
      window.setBackgroundBlurRadius(30)
    }
  }
}
