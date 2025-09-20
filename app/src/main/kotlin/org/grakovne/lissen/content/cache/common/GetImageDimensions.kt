package org.grakovne.lissen.content.cache.common

import android.graphics.BitmapFactory
import java.io.File

fun getImageDimensions(file: File): Pair<Int, Int>? =
  try {
    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    file.inputStream().use { fis ->
      BitmapFactory.decodeStream(fis, null, boundsOptions)
    }
    boundsOptions.outWidth to boundsOptions.outHeight
  } catch (e: Exception) {
    null
  }
