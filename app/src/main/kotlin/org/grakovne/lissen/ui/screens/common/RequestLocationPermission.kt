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
fun RequestLocationPermission() {
  val context = LocalContext.current

  val permissionRequestLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = {},
    )

  LaunchedEffect(Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val permissionStatus =
        ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.ACCESS_FINE_LOCATION,
        )

      when (permissionStatus == PackageManager.PERMISSION_GRANTED) {
        true -> {}
        false -> permissionRequestLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
      }
    }
  }
}
