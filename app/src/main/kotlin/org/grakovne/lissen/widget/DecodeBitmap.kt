package org.grakovne.lissen.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

fun decodeSampledBitmapFromResource(
  context: Context,
  resId: Int,
  reqWidthPx: Int,
  reqHeightPx: Int,
): Bitmap {
  val bounds =
    BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
  BitmapFactory.decodeResource(context.resources, resId, bounds)

  val options =
    BitmapFactory.Options().apply {
      inSampleSize = calculateInSampleSize(bounds, reqWidthPx, reqHeightPx)
      inPreferredConfig = Bitmap.Config.RGB_565
      inDither = true
    }

  return BitmapFactory.decodeResource(context.resources, resId, options)
}

fun calculateInSampleSize(
  options: BitmapFactory.Options,
  reqWidthPx: Int,
  reqHeightPx: Int,
): Int {
  val srcWidth = options.outWidth
  val srcHeight = options.outHeight
  var inSampleSize = 1

  if (srcHeight > reqHeightPx || srcWidth > reqWidthPx) {
    val halfHeight = srcHeight / 2
    val halfWidth = srcWidth / 2

    while (
      halfHeight / inSampleSize >= reqHeightPx &&
      halfWidth / inSampleSize >= reqWidthPx
    ) {
      inSampleSize *= 2
    }
  }

  return inSampleSize.coerceAtLeast(1)
}
