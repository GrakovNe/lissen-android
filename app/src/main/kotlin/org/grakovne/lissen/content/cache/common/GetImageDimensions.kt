package org.grakovne.lissen.content.cache.common

import android.graphics.BitmapFactory
import okio.Buffer

fun getImageDimensions(buffer: Buffer): Pair<Int, Int>? =
  try {
    val boundsOptions =
      BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }

    val peekedSource = buffer.peek()
    BitmapFactory.decodeStream(peekedSource.inputStream(), null, boundsOptions)
    boundsOptions.outWidth to boundsOptions.outHeight
  } catch (ex: Exception) {
    null
  }
