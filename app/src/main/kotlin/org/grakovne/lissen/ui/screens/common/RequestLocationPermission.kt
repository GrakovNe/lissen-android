package org.grakovne.lissen.ui.screens.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

fun hasLocationPermission(context: Context): Boolean =
  when (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    true -> ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    false -> true
  }
