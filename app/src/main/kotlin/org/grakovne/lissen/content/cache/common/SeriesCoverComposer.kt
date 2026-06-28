package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import okio.Buffer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SeriesCoverComposer
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) {
    fun compose(covers: List<File>): Buffer? {
      val bitmaps =
        covers
          .take(MAX_COVERS)
          .mapNotNull { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

      if (bitmaps.isEmpty()) {
        return null
      }

      val density = context.resources.displayMetrics.density
      val canvasSize = (SIZE_DP * density).roundToInt()
      val step = STEP_DP * density
      val corner = CORNER_DP * density
      val shadowRadius = SHADOW_DP * density
      val cardSize = canvasSize - step * (bitmaps.size - 1)

      val result = createBitmap(canvasSize, canvasSize)
      val canvas = Canvas(result)

      bitmaps
        .asReversed()
        .forEachIndexed { index, bitmap ->
          val origin = step * index
          val rect = RectF(origin, origin, origin + cardSize, origin + cardSize)

          val shadowPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
              color = CARD_COLOR
              setShadowLayer(shadowRadius, 0f, shadowRadius / 2, SHADOW_COLOR)
            }
          canvas.drawRoundRect(rect, corner, corner, shadowPaint)

          canvas.save()
          canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
          canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
          canvas.restore()
        }

      bitmaps.forEach { it.recycle() }

      return Buffer().also { buffer ->
        result.compress(Bitmap.CompressFormat.PNG, 100, buffer.outputStream())
        result.recycle()
      }
    }

    companion object {
      const val MAX_COVERS = 3

      private const val SIZE_DP = 64f
      private const val STEP_DP = 6f
      private const val CORNER_DP = 4f
      private const val SHADOW_DP = 2f
      private const val CARD_COLOR = 0xFFFFFFFF.toInt()
      private const val SHADOW_COLOR = 0x40000000
    }
  }
