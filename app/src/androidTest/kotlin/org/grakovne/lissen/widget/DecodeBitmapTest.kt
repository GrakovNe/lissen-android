package org.grakovne.lissen.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.grakovne.lissen.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class DecodeBitmapTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun decodesFallbackResourceIntoNonEmptyBitmap() {
    val bitmap = bitmapFromResource(context, R.drawable.cover_fallback_png, 80, 80)

    assertTrue(bitmap.width > 0)
    assertTrue(bitmap.height > 0)
  }

  @Test
  fun downsamplesLargeFileWhenTargetIsSmall() {
    val source = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
    val file = writePng(source, "large-cover.png")

    val decoded = bitmapFromFile(file.absolutePath, 80, 80)

    assertTrue("expected downsampled width, got ${decoded.width}", decoded.width <= 200)
    assertTrue("expected downsampled height, got ${decoded.height}", decoded.height <= 200)
  }

  @Test
  fun keepsSmallFileAtFullResolution() {
    val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
    val file = writePng(source, "small-cover.png")

    val decoded = bitmapFromFile(file.absolutePath, 80, 80)

    assertEquals(64, decoded.width)
    assertEquals(64, decoded.height)
  }

  private fun writePng(
    bitmap: Bitmap,
    name: String,
  ): File {
    val file = File(context.cacheDir, name)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
  }
}
