package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.hoko.blur.HokoBlur
import com.hoko.blur.HokoBlur.MODE_GAUSSIAN
import com.hoko.blur.HokoBlur.SCHEME_NATIVE
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Returns a square cover for this image: the file itself when it is already
 * square, otherwise a temp file with a blurred square backdrop.
 */
fun File.withBlur(context: Context): File {
  val bounds =
    BitmapFactory
      .Options()
      .apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)

  val width = bounds.outWidth
  val height = bounds.outHeight

  if (width <= 0 || height <= 0 || width == height) {
    return this
  }

  return runCatching { blurredBackdropFile(this, context) }.getOrElse { this }
}

private fun blurredBackdropFile(
  source: File,
  context: Context,
): File {
  val original = BitmapFactory.decodeFile(source.path) ?: return source

  val width = original.width
  val height = original.height
  val size = max(width, height)

  val backdrop = buildBlurredBackdrop(original, size, context)

  val result = createBitmap(size, size, Bitmap.Config.RGB_565)
  val canvas = Canvas(result)

  canvas.drawBitmap(
    backdrop,
    null,
    Rect(0, 0, size, size),
    Paint(Paint.FILTER_BITMAP_FLAG),
  )
  backdrop.recycle()

  val left = ((size - width) / 2f)
  val top = ((size - height) / 2f)

  canvas.drawBitmap(original, left, top, null)
  original.recycle()

  val output = File.createTempFile("blurred_", ".png", context.cacheDir)
  FileOutputStream(output).use { stream ->
    result.compress(Bitmap.CompressFormat.PNG, 100, stream)
  }
  result.recycle()

  return output
}

private fun buildBlurredBackdrop(
  original: Bitmap,
  size: Int,
  context: Context,
): Bitmap {
  val radius =
    (BASE_RADIUS.toFloat() * BACKDROP_WORK_SIZE / size)
      .roundToInt()
      .coerceIn(1, BASE_RADIUS)
  val padding = radius * 2

  val padded = original.scale(BACKDROP_WORK_SIZE + padding, BACKDROP_WORK_SIZE + padding)

  val blurred =
    HokoBlur
      .with(context)
      .scheme(SCHEME_NATIVE)
      .mode(MODE_GAUSSIAN)
      .radius(radius)
      .forceCopy(true)
      .blur(padded)

  if (blurred !== padded) {
    padded.recycle()
  }

  val cropped = Bitmap.createBitmap(blurred, padding / 2, padding / 2, BACKDROP_WORK_SIZE, BACKDROP_WORK_SIZE)
  if (cropped !== blurred) {
    blurred.recycle()
  }

  return cropped
}

private const val BASE_RADIUS = 32
private const val BACKDROP_WORK_SIZE = 512
