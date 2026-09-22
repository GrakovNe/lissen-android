package org.grakovne.lissen.common

import android.content.Context
import java.io.File

/**
 * External cache dir when it is mounted and writable, otherwise the internal one.
 */
fun Context.preferredCacheDir(): File =
  externalCacheDir
    ?.takeIf { it.exists() && it.canWrite() }
    ?: cacheDir
