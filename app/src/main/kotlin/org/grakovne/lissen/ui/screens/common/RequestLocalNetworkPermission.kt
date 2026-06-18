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
import java.net.InetAddress
import java.net.URI

fun hasLocalNetworkPermission(context: Context): Boolean =
  when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
    true -> ContextCompat.checkSelfPermission(context, localNetworkPermission()) == PackageManager.PERMISSION_GRANTED
    false -> true
  }

fun localNetworkPermission(): String = "android.permission.ACCESS_LOCAL_NETWORK"

@Composable
fun RequestLocalNetworkPermission(
  host: String?,
  onGranted: () -> Unit,
) {
  val context = LocalContext.current

  val permissionRequestLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { isGranted -> if (isGranted) onGranted() },
    )

  LaunchedEffect(Unit) {
    if (host != null && isLocalNetworkHost(host) && !hasLocalNetworkPermission(context)) {
      permissionRequestLauncher.launch(localNetworkPermission())
    }
  }
}

fun isLocalNetworkHost(url: String): Boolean {
  val host =
    try {
      URI(url).host ?: url
    } catch (_: Exception) {
      url
    }

  if (host.endsWith(".local", ignoreCase = true)) return true

  return try {
    val host = InetAddress.getByName(host)

    host.isSiteLocalAddress || host.isLoopbackAddress || host.isLinkLocalAddress
  } catch (_: Exception) {
    false
  }
}
