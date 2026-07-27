package org.grakovne.lissen.content.cache.temporary

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ShortTermCacheStoragePropertiesTest {
  private fun properties(dir: File): ShortTermCacheStorageProperties {
    val context =
      mockk<Context> {
        every { externalCacheDir } returns dir
      }
    return ShortTermCacheStorageProperties(context)
  }

  @Test
  fun `cover path is a flat hex file inside the cover cache folder`(
    @TempDir dir: File,
  ) {
    val properties = properties(dir)

    val path = properties.provideCoverPath("book-1")

    assertEquals(properties.provideCoverCacheFolder(), path.parentFile)
    assertTrue(path.name.matches(Regex("[0-9a-f]{64}")))
  }

  @Test
  fun `unsafe characters in item id do not escape the cover cache folder`(
    @TempDir dir: File,
  ) {
    val properties = properties(dir)

    val path = properties.provideCoverPath("li_a/b\\c:d")

    assertEquals(properties.provideCoverCacheFolder(), path.parentFile)
    assertTrue(path.name.matches(Regex("[0-9a-f]{64}")))
  }

  @Test
  fun `cover path is deterministic and distinct per item`(
    @TempDir dir: File,
  ) {
    val properties = properties(dir)

    assertEquals(properties.provideCoverPath("book-1"), properties.provideCoverPath("book-1"))
    assertNotEquals(properties.provideCoverPath("book-1"), properties.provideCoverPath("book-2"))
  }

  @Test
  fun `falls back to internal cache when external cache is unavailable`(
    @TempDir dir: File,
  ) {
    val context =
      mockk<Context> {
        every { externalCacheDir } returns null
        every { cacheDir } returns dir
      }
    val properties = ShortTermCacheStorageProperties(context)

    val path = properties.provideCoverPath("book-1")

    assertTrue(path.startsWith(dir))
  }
}
