package org.grakovne.lissen.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

fun bitmapFromResource(
  context: Context,
  resourceId: Int,
  widthPx: Int,
  heightPx: Int,
): Bitmap {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

  BitmapFactory.decodeResource(context.resources, resourceId, bounds)

  val options =
    BitmapFactory.Options().apply {
      inSampleSize = calculateInSampleSize(bounds, widthPx, heightPx)
      inPreferredConfig = Bitmap.Config.RGB_565
    }

  return BitmapFactory.decodeResource(context.resources, resourceId, options)
}

fun bitmapFromFile(
  path: String,
  widthPx: Int,
  heightPx: Int,
): Bitmap {
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeFile(path, bounds)

  val options =
    BitmapFactory.Options().apply {
      inSampleSize = calculateInSampleSize(bounds, widthPx, heightPx)
      inPreferredConfig = Bitmap.Config.RGB_565
    }

  return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(
  options: BitmapFactory.Options,
  widthPx: Int,
  heightPx: Int,
): Int {
  val srcWidth = options.outWidth
  val srcHeight = options.outHeight
  var inSampleSize = 1

  if (srcHeight > heightPx || srcWidth > widthPx) {
    val halfHeight = srcHeight / 2
    val halfWidth = srcWidth / 2

    while (
      halfHeight / inSampleSize >= heightPx ||
      halfWidth / inSampleSize >= widthPx
    ) {
      inSampleSize *= 2
    }
  }

  return inSampleSize.coerceAtLeast(1)
}
