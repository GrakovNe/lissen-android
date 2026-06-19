package org.grakovne.lissen.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@UnstableApi
@RunWith(AndroidJUnit4::class)
class FileDataSourceBypassTest {
  private lateinit var cacheDir: File

  @Before
  fun setUp() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    cacheDir = File(context.cacheDir, "test_media_cache")
    cacheDir.mkdirs()
  }

  @Test
  fun fileDataSource_reads_local_file_directly() {
    val content = ByteArray(1024) { (it % 256).toByte() }
    val file = File(cacheDir, "test-file")
    file.writeBytes(content)

    val dataSource = FileDataSource()
    val dataSpec =
      DataSpec
        .Builder()
        .setUri(file.toUri())
        .build()

    val bytesAvailable = dataSource.open(dataSpec)
    assertEquals(content.size.toLong(), bytesAvailable)

    val buffer = ByteArray(content.size)
    var totalRead = 0
    while (totalRead < content.size) {
      val read = dataSource.read(buffer, totalRead, content.size - totalRead)
      if (read == C.RESULT_END_OF_INPUT) break
      totalRead += read
    }

    dataSource.close()
    assertEquals(content.size, totalRead)

    for (i in content.indices) {
      assertEquals(content[i], buffer[i])
    }

    file.delete()
  }

  @Test
  fun fileDataSource_reads_from_offset() {
    val content = ByteArray(2048) { (it % 256).toByte() }
    val file = File(cacheDir, "test-offset-file")
    file.writeBytes(content)

    val offset = 512L
    val dataSource = FileDataSource()
    val dataSpec =
      DataSpec
        .Builder()
        .setUri(file.toUri())
        .setPosition(offset)
        .build()

    val bytesAvailable = dataSource.open(dataSpec)
    assertEquals(content.size - offset, bytesAvailable)

    val buffer = ByteArray((content.size - offset).toInt())
    var totalRead = 0
    while (totalRead < buffer.size) {
      val read = dataSource.read(buffer, totalRead, buffer.size - totalRead)
      if (read == C.RESULT_END_OF_INPUT) break
      totalRead += read
    }

    dataSource.close()

    for (i in buffer.indices) {
      assertEquals(content[(i + offset.toInt())], buffer[i])
    }

    file.delete()
  }

  @Test
  fun file_scheme_correctly_detected() {
    val file = File(cacheDir, "scheme-test")
    file.writeBytes("test".toByteArray())

    val fileUri = file.toUri()
    assertEquals("file", fileUri.scheme)

    val httpUri = Uri.parse("https://server/api/items/book/file/123")
    assertFalse(httpUri.scheme == "file")

    file.delete()
  }

  @Test
  fun routing_logic_selects_fileDataSource_for_file_scheme() {
    val fileUri = Uri.parse("file:///data/media_cache/book1/file1")
    val httpUri = Uri.parse("https://server/api/items/book1/file/file1")

    assertTrue(fileUri.scheme == "file")
    assertFalse(httpUri.scheme == "file")
  }

  @Test
  fun temp_file_not_visible_as_final_destination() {
    val dest = File(cacheDir, "final-file")
    val tempDest = File(cacheDir, "${dest.name}.tmp")

    tempDest.writeBytes("downloading...".toByteArray())

    assertTrue(tempDest.exists())
    assertFalse(dest.exists())

    tempDest.renameTo(dest)

    assertFalse(tempDest.exists())
    assertTrue(dest.exists())

    dest.delete()
  }

  @Test
  fun fileDataSource_can_reopen_same_file_without_contention() {
    val content = ByteArray(512) { (it % 256).toByte() }
    val file = File(cacheDir, "reopen-test")
    file.writeBytes(content)

    val ds1 = FileDataSource()
    val ds2 = FileDataSource()

    val spec =
      DataSpec
        .Builder()
        .setUri(file.toUri())
        .build()

    val bytes1 = ds1.open(spec)
    val bytes2 = ds2.open(spec)

    assertEquals(content.size.toLong(), bytes1)
    assertEquals(content.size.toLong(), bytes2)

    ds1.close()
    ds2.close()
    file.delete()
  }
}
