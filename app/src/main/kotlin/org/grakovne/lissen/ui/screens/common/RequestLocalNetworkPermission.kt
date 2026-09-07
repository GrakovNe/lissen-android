package org.grakovne.lissen.ui.screens.common

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

fun hasLocalNetworkPermission(context: Context): Boolean =
  when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
    true -> ContextCompat.checkSelfPermission(context, localNetworkPermission()) == PackageManager.PERMISSION_GRANTED
    false -> true
  }

fun localNetworkPermission(): String = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun RequestLocalNetworkPermission(onGranted: () -> Unit) {
  val context = LocalContext.current

  val permissionRequestLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { isGranted -> if (isGranted) onGranted() },
    )

  LaunchedEffect(Unit) {
    if (!hasLocalNetworkPermission(context)) {
      permissionRequestLauncher.launch(localNetworkPermission())
    }
  }
}
