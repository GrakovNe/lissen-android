package org.grakovne.lissen.content.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.hoko.blur.HokoBlur
import com.hoko.blur.HokoBlur.MODE_STACK
import com.hoko.blur.HokoBlur.SCHEME_NATIVE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSource

suspend fun sourceWithBackdropBlur(
  source: BufferedSource,
  context: Context,
): Buffer =
  withContext(Dispatchers.IO) {
    val peeked = source.peek()

    val original = BitmapFactory.decodeStream(peeked.inputStream())
    val width = original.width
    val height = original.height

    val size = maxOf(width, height)

    val blurred =
      HokoBlur
        .with(context)
        .scheme(SCHEME_NATIVE)
        .mode(MODE_STACK)
        .radius(24)
        .forceCopy(true)
        .blur(original.scale(size, size))

    val result = createBitmap(size, size)
    val canvas = Canvas(result)
    canvas.drawBitmap(blurred, 0f, 0f, null)

    val left = ((size - width) / 2f)
    val top = ((size - height) / 2f)
    canvas.drawBitmap(original, left, top, null)

    val buffer = Buffer()
    result.compress(Bitmap.CompressFormat.JPEG, 90, buffer.outputStream())

    buffer
  }
