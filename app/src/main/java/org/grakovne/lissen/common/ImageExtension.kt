package org.grakovne.lissen.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun String.fromBase64(): Bitmap? =
  try {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  } catch (ex: Exception) {
    null
  }

fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
