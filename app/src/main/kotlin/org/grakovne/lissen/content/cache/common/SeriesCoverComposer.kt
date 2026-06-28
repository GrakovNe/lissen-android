package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
      val cardSize = canvasSize - step * (bitmaps.size - 1)

      val result = createBitmap(canvasSize, canvasSize)
      val canvas = Canvas(result)

      bitmaps
        .asReversed()
        .forEachIndexed { index, bitmap ->
          val origin = step * index
          val rect = RectF(origin, origin, origin + cardSize, origin + cardSize)

          val matrix =
            Matrix().apply {
              setScale(cardSize / bitmap.width, cardSize / bitmap.height)
              postTranslate(origin, origin)
            }
          val imagePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
              isFilterBitmap = true
              shader =
                BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                  .apply { setLocalMatrix(matrix) }
            }
          canvas.drawRoundRect(rect, corner, corner, imagePaint)
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
    }
  }
