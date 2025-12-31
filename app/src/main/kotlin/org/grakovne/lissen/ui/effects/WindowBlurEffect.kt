package org.grakovne.lissen.ui.effects

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
      val window = view.context as? Window ?: return@SideEffect
      window.setBackgroundBlurRadius(30)
    }
  }
}
