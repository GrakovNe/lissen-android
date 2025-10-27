package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.hoko.blur.HokoBlur
import com.hoko.blur.HokoBlur.MODE_STACK
import com.hoko.blur.HokoBlur.SCHEME_OPENGL
import okio.Buffer
import okio.BufferedSource

fun Buffer.withBlur(context: Context): Buffer {
  val dimensions: Pair<Int, Int>? = getImageDimensions(this)

  return when (dimensions?.first == dimensions?.second) {
    true -> this
    false -> runCatching { sourceWithBackdropBlur(this, context) }.getOrElse { this }
  }
}

private fun sourceWithBackdropBlur(
  source: BufferedSource,
  context: Context,
): Buffer {
  val peeked = source.peek()

  val original = BitmapFactory.decodeStream(peeked.inputStream())
  val width = original.width
  val height = original.height

  val size = maxOf(width, height)

  val radius = 24
  val padding = radius * 2

  val scaled = original.scale(size + padding, size + padding)

  val blurredPadded =
    HokoBlur
      .with(context)
      .scheme(SCHEME_OPENGL)
      .mode(MODE_STACK)
      .radius(radius)
      .forceCopy(true)
      .blur(scaled)

  val backdrop = Bitmap.createBitmap(blurredPadded, padding / 2, padding / 2, size, size)

  val result = createBitmap(size, size, Bitmap.Config.RGB_565)

  val canvas = Canvas(result)
  canvas.drawBitmap(backdrop, 0f, 0f, null)

  val left = ((size - width) / 2f)
  val top = ((size - height) / 2f)

  canvas.drawBitmap(original, left, top, null)

  return Buffer().apply { result.compress(Bitmap.CompressFormat.JPEG, 90, this.outputStream()) }
}
