package org.grakovne.lissen.ui.screens.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun RequestNotificationPermissions() {
  val context = LocalContext.current

  val permissionRequestLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { },
    )
  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permissionStatus =
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS,
        )

      when (permissionStatus == PackageManager.PERMISSION_GRANTED) {
        true -> {}
        false -> permissionRequestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}
