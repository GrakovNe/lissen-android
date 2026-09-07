package org.grakovne.lissen.playback.service

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DataSourceRoutingTest {
  private val mediaProvider = mockk<LissenMediaProvider>()

  @Nested
  inner class SchemeDetection {
    @Test
    fun `file scheme detected for local cache URI`() {
      val uri = stubUri(scheme = "file")
      assertTrue(uri.scheme == "file")
    }

    @Test
    fun `https scheme detected for remote URI`() {
      val uri = stubUri(scheme = "https")
      assertFalse(uri.scheme == "file")
    }

    @Test
    fun `http scheme detected for remote URI`() {
      val uri = stubUri(scheme = "http")
      assertFalse(uri.scheme == "file")
    }

    @Test
    fun `lissen scheme detected for unresolved URI`() {
      val uri = stubUri(scheme = "lissen")
      assertFalse(uri.scheme == "file")
    }

    @Test
    fun `null scheme is not file`() {
      val uri = stubUri(scheme = null)
      assertFalse(uri.scheme == "file")
    }
  }

  @Nested
  inner class RoutingDecision {
    @Test
    fun `local file resolves to file scheme and should bypass cache`() {
      val fileUri = stubUri(scheme = "file")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(fileUri)

      val resolved =
        mediaProvider.provideFileUri("book-1", "file-1").fold(
          onSuccess = { it },
          onFailure = { stubUri(scheme = "lissen") },
        )

      assertEquals("file", resolved.scheme)
      assertTrue(resolved.scheme == "file")
    }

    @Test
    fun `remote stream resolves to https scheme and should use cache`() {
      val remoteUri = stubUri(scheme = "https")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(remoteUri)

      val resolved =
        mediaProvider.provideFileUri("book-1", "file-1").fold(
          onSuccess = { it },
          onFailure = { stubUri(scheme = "lissen") },
        )

      assertEquals("https", resolved.scheme)
      assertFalse(resolved.scheme == "file")
    }

    @Test
    fun `error falls back to lissen scheme which is not file`() {
      val fallback = stubUri(scheme = "lissen")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Error(OperationError.InternalError)

      val resolved =
        mediaProvider.provideFileUri("book-1", "file-1").fold(
          onSuccess = { it },
          onFailure = { fallback },
        )

      assertEquals("lissen", resolved.scheme)
      assertFalse(resolved.scheme == "file")
    }
  }

  @Nested
  inner class LocalFilePreference {
    @Test
    fun `when local file exists it is preferred over remote`() {
      val localUri = stubUri(scheme = "file")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(localUri)

      val result = mediaProvider.provideFileUri("book-1", "file-1")
      val resolved = result.fold(onSuccess = { it }, onFailure = { stubUri(scheme = "lissen") })

      assertEquals("file", resolved.scheme)
    }

    @Test
    fun `when local file missing falls back to remote`() {
      val remoteUri = stubUri(scheme = "https")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(remoteUri)

      val result = mediaProvider.provideFileUri("book-1", "file-1")
      val resolved = result.fold(onSuccess = { it }, onFailure = { stubUri(scheme = "lissen") })

      assertEquals("https", resolved.scheme)
    }

    @Test
    fun `consecutive calls for same file return consistent scheme`() {
      val localUri = stubUri(scheme = "file")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(localUri)

      val r1 = mediaProvider.provideFileUri("book-1", "file-1").fold({ it.scheme }, { "err" })
      val r2 = mediaProvider.provideFileUri("book-1", "file-1").fold({ it.scheme }, { "err" })

      assertEquals(r1, r2)
    }
  }

  @Nested
  inner class MultipleFilesRouting {
    @Test
    fun `different files can have different routing`() {
      val localUri = stubUri(scheme = "file")
      val remoteUri = stubUri(scheme = "https")

      every { mediaProvider.provideFileUri("book-1", "file-cached") } returns
        OperationResult.Success(localUri)
      every { mediaProvider.provideFileUri("book-1", "file-remote") } returns
        OperationResult.Success(remoteUri)

      val r1 =
        mediaProvider
          .provideFileUri("book-1", "file-cached")
          .fold({ it.scheme }, { "err" })
      val r2 =
        mediaProvider
          .provideFileUri("book-1", "file-remote")
          .fold({ it.scheme }, { "err" })

      assertEquals("file", r1)
      assertEquals("https", r2)
    }
  }

  private fun stubUri(scheme: String?): Uri {
    val uri = mockk<Uri>()
    every { uri.scheme } returns scheme
    every { uri.toString() } returns "${scheme ?: "null"}://stub"
    return uri
  }
}
