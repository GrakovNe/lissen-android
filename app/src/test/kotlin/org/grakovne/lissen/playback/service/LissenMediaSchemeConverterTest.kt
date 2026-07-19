package org.grakovne.lissen.playback.service

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenMediaSchemeConverterTest {
  @Nested
  inner class Unapply {
    @Test
    fun `returns null for non-lissen scheme`() {
      val uri = stubUri(scheme = "https", segments = listOf("api", "items"))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for null scheme`() {
      val uri = stubUri(scheme = null, segments = listOf("book1", "file1"))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for lissen URI with single segment`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("book1"))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for lissen URI with three segments`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("book1", "file1", "extra"))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for lissen URI with empty segments`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("", ""))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for lissen URI with empty first segment`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("", "file1"))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for lissen URI with empty second segment`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("book1", ""))
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `returns null for empty segment list`() {
      val uri = stubUri(scheme = "lissen", segments = emptyList())
      assertNull(parseLissenUri(uri))
    }

    @Test
    fun `parses valid lissen URI`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("book-123", "file-456"))
      val result = parseLissenUri(uri)
      assertNotNull(result)
      assertEquals("book-123" to "file-456", result)
    }

    @Test
    fun `parses lissen URI with underscore ids`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("li_abc123", "ino_xyz789"))
      val result = parseLissenUri(uri)
      assertNotNull(result)
      assertEquals("li_abc123", result!!.first)
      assertEquals("ino_xyz789", result.second)
    }

    @Test
    fun `parses lissen URI with numeric ids`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("123456", "789012"))
      val result = parseLissenUri(uri)
      assertNotNull(result)
      assertEquals("123456", result!!.first)
      assertEquals("789012", result.second)
    }
  }

  private fun stubUri(
    scheme: String?,
    segments: List<String>,
  ): Uri {
    val uri = mockk<Uri>()
    every { uri.scheme } returns scheme
    every { uri.pathSegments } returns segments
    return uri
  }
}
