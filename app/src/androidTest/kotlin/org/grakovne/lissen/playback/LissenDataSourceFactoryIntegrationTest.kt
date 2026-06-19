package org.grakovne.lissen.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.playback.service.apply
import org.grakovne.lissen.playback.service.unapply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LissenDataSourceFactoryIntegrationTest {
  private val mediaProvider = mockk<LissenMediaProvider>()

  @Test
  fun apply_creates_valid_lissen_uri() {
    val uri = apply("book-123", "file-456")
    assertEquals("lissen", uri.scheme)
    assertEquals(2, uri.pathSegments.size)
    assertEquals("book-123", uri.pathSegments[0])
    assertEquals("file-456", uri.pathSegments[1])
  }

  @Test
  fun unapply_parses_valid_lissen_uri() {
    val uri = apply("book-abc", "file-xyz")
    val result = unapply(uri)
    assertNotNull(result)
    assertEquals("book-abc", result!!.first)
    assertEquals("file-xyz", result.second)
  }

  @Test
  fun apply_unapply_roundtrip() {
    val bookId = "li_abcdef123456"
    val fileId = "ino_ghijkl789012"
    val uri = apply(bookId, fileId)
    val (parsedBookId, parsedFileId) = unapply(uri)!!
    assertEquals(bookId, parsedBookId)
    assertEquals(fileId, parsedFileId)
  }

  @Test
  fun unapply_returns_null_for_http_uri() {
    val uri = Uri.parse("https://server/api/items/book1/file/file1")
    assertNull(unapply(uri))
  }

  @Test
  fun unapply_returns_null_for_file_uri() {
    val uri = Uri.parse("file:///data/media_cache/book1/file1")
    assertNull(unapply(uri))
  }

  @Test
  fun unapply_returns_null_for_lissen_uri_with_single_segment() {
    val uri = Uri.parse("lissen://book1")
    assertNull(unapply(uri))
  }

  @Test
  fun unapply_returns_null_for_lissen_uri_with_three_segments() {
    val uri = Uri.parse("lissen://host/book1/file1/extra")
    val result = unapply(uri)
    // 3 segments → null
    assertNull(result)
  }

  @Test
  fun uri_resolution_returns_local_file_uri_when_cached() {
    val lissenUri = apply("book-1", "file-1")
    val localUri = Uri.parse("file:///data/media_cache/book-1/file-1")

    every { mediaProvider.provideFileUri("book-1", "file-1") } returns
      OperationResult.Success(localUri)

    val (bookId, fileId) = unapply(lissenUri)!!
    val resolved =
      mediaProvider.provideFileUri(bookId, fileId).fold(
        onSuccess = { it },
        onFailure = { lissenUri },
      )

    assertEquals("file", resolved.scheme)
    assertEquals("/data/media_cache/book-1/file-1", resolved.path)
  }

  @Test
  fun uri_resolution_returns_remote_uri_when_not_cached() {
    val lissenUri = apply("book-1", "file-1")
    val remoteUri = Uri.parse("https://server:8080/api/items/book-1/file/file-1")

    every { mediaProvider.provideFileUri("book-1", "file-1") } returns
      OperationResult.Success(remoteUri)

    val (bookId, fileId) = unapply(lissenUri)!!
    val resolved =
      mediaProvider.provideFileUri(bookId, fileId).fold(
        onSuccess = { it },
        onFailure = { lissenUri },
      )

    assertEquals("https", resolved.scheme)
    assertEquals("server", resolved.host)
  }

  @Test
  fun uri_resolution_falls_back_to_lissen_uri_on_error() {
    val lissenUri = apply("book-1", "file-1")

    every { mediaProvider.provideFileUri("book-1", "file-1") } returns
      OperationResult.Error(OperationError.InternalError)

    val (bookId, fileId) = unapply(lissenUri)!!
    val resolved =
      mediaProvider.provideFileUri(bookId, fileId).fold(
        onSuccess = { it },
        onFailure = { lissenUri },
      )

    assertEquals("lissen", resolved.scheme)
  }

  @Test
  fun dataspec_preserves_position_after_uri_replacement() {
    val lissenUri = apply("book-1", "file-1")
    val localUri = Uri.parse("file:///data/media_cache/book-1/file-1")

    val original =
      DataSpec
        .Builder()
        .setUri(lissenUri)
        .setPosition(12345L)
        .build()

    val resolved =
      original
        .buildUpon()
        .setUri(localUri)
        .build()

    assertEquals(localUri, resolved.uri)
    assertEquals(12345L, resolved.position)
  }

  @Test
  fun dataspec_preserves_length_after_uri_replacement() {
    val lissenUri = apply("book-1", "file-1")
    val remoteUri = Uri.parse("https://server/api/items/book-1/file/file-1")

    val original =
      DataSpec
        .Builder()
        .setUri(lissenUri)
        .setPosition(0)
        .setLength(999999L)
        .build()

    val resolved =
      original
        .buildUpon()
        .setUri(remoteUri)
        .build()

    assertEquals(remoteUri, resolved.uri)
    assertEquals(999999L, resolved.length)
    assertEquals(0L, resolved.position)
  }

  @Test
  fun apply_handles_ids_with_underscores_and_dashes() {
    val bookId = "li_abc-123_def"
    val fileId = "ino_xyz-789_ghi"
    val uri = apply(bookId, fileId)
    val result = unapply(uri)
    assertNotNull(result)
    assertEquals(bookId, result!!.first)
    assertEquals(fileId, result.second)
  }

  @Test
  fun apply_handles_numeric_ids() {
    val uri = apply("123456", "789012")
    val result = unapply(uri)
    assertNotNull(result)
    assertEquals("123456", result!!.first)
    assertEquals("789012", result.second)
  }
}
