package org.grakovne.lissen.content.cache.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import okio.Buffer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class SeriesCoverComposer
  @Inject
  constructor() {
    fun compose(covers: List<File>): Buffer? {
      val bitmaps =
        covers
          .mapNotNull { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

      if (bitmaps.isEmpty()) {
        return null
      }

      val cardSize = bitmaps.maxOf { maxOf(it.width, it.height) }.toFloat()
      val step = cardSize * STEP_RATIO
      val corner = cardSize * CORNER_RATIO
      val canvasSize = (cardSize + step * (bitmaps.size - 1)).roundToInt()

      val result = createBitmap(canvasSize, canvasSize)
      val canvas = Canvas(result)

      bitmaps.indices.reversed().forEach { index ->
        val bitmap = bitmaps[index]
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
      private const val STEP_RATIO = 6f / 64f
      private const val CORNER_RATIO = 4f / 64f
    }
  }
