package org.grakovne.lissen.common

import android.content.Context
import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import com.hoko.blur.HokoBlur
import com.hoko.blur.HokoBlur.MODE_STACK
import com.hoko.blur.HokoBlur.SCHEME_NATIVE

class HokoBlurTransformation(
  private val context: Context,
) : Transformation {
  override val cacheKey: String = "HokoBlurTransformation(radius=24)"

  override suspend fun transform(
    input: Bitmap,
    size: Size,
  ): Bitmap =
    try {
      HokoBlur
        .with(context)
        .scheme(SCHEME_NATIVE)
        .mode(MODE_STACK)
        .radius(24)
        .forceCopy(true)
        .blur(input)
    } catch (ex: Exception) {
      input
    }
}
