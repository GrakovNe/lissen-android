package org.grakovne.lissen.content.cache.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SeriesCoverComposerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val composer = SeriesCoverComposer()

  @Test
  fun stacksColoredCoversWithTheFirstOnTopAndTheLastPeekingOutAtTheCorner() {
    val red = solidCoverFile("red.png", Color.RED)
    val green = solidCoverFile("green.png", Color.GREEN)
    val blue = solidCoverFile("blue.png", Color.BLUE)

    val composed = composer.compose(listOf(red, green, blue))
    assertNotNull(composed)

    val result = BitmapFactory.decodeStream(composed!!.inputStream())

    assertEquals(119, result.width)
    assertEquals(119, result.height)

    assertEquals("topmost cover should be the first file", Color.RED, result.getPixel(50, 50))
    assertEquals("last cover should peek out at the bottom-right corner", Color.BLUE, result.getPixel(114, 114))
  }

  private fun solidCoverFile(
    name: String,
    color: Int,
  ): File {
    val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawColor(color)

    val file = File(context.cacheDir, name)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
  }
}
