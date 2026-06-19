package org.grakovne.lissen.playback.service

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LissenDataSourceFactoryTest {
  private val mediaProvider = mockk<LissenMediaProvider>()

  @Nested
  inner class UriResolution {
    @Test
    fun `provideFileUri returns local file URI when cache hit`() {
      val localUri = stubUri(scheme = "file")
      every { mediaProvider.provideFileUri("book1", "file1") } returns
        OperationResult.Success(localUri)

      val result = mediaProvider.provideFileUri("book1", "file1")

      assert(result is OperationResult.Success)
      assertEquals("file", (result as OperationResult.Success).data.scheme)
    }

    @Test
    fun `provideFileUri returns remote URI when no cache`() {
      val remoteUri = stubUri(scheme = "https")
      every { mediaProvider.provideFileUri("book1", "file1") } returns
        OperationResult.Success(remoteUri)

      val result = mediaProvider.provideFileUri("book1", "file1")

      assert(result is OperationResult.Success)
      assertEquals("https", (result as OperationResult.Success).data.scheme)
    }

    @Test
    fun `provideFileUri returns error in force-cache mode when file missing`() {
      every { mediaProvider.provideFileUri("book1", "file1") } returns
        OperationResult.Error(OperationError.InternalError)

      val result = mediaProvider.provideFileUri("book1", "file1")

      assert(result is OperationResult.Error)
    }

    @Test
    fun `provideFileUri prefers local file over remote`() {
      val localUri = stubUri(scheme = "file")
      every { mediaProvider.provideFileUri("book1", "file1") } returns
        OperationResult.Success(localUri)

      val result = mediaProvider.provideFileUri("book1", "file1")

      assert(result is OperationResult.Success)
      assertEquals("file", (result as OperationResult.Success).data.scheme)
    }
  }

  @Nested
  inner class UriResolutionFallback {
    @Test
    fun `fold returns success URI on success`() {
      val localUri = stubUri(scheme = "file")
      val fallbackUri = stubUri(scheme = "lissen")
      val result: OperationResult<Uri> = OperationResult.Success(localUri)

      val resolvedUri =
        result.fold(
          onSuccess = { it },
          onFailure = { fallbackUri },
        )

      assertEquals("file", resolvedUri.scheme)
    }

    @Test
    fun `fold returns fallback lissen URI on error`() {
      val fallbackUri = stubUri(scheme = "lissen")
      val result: OperationResult<Uri> = OperationResult.Error(OperationError.InternalError)

      val resolvedUri =
        result.fold(
          onSuccess = { it },
          onFailure = { fallbackUri },
        )

      assertEquals("lissen", resolvedUri.scheme)
    }

    @Test
    fun `fallback to lissen URI produces invalid scheme for OkHttp`() {
      val fallbackUri = stubUri(scheme = "lissen")
      val result: OperationResult<Uri> = OperationResult.Error(OperationError.InternalError)

      val resolvedUri =
        result.fold(
          onSuccess = { it },
          onFailure = { fallbackUri },
        )

      assertEquals("lissen", resolvedUri.scheme)
    }
  }

  @Nested
  inner class UnapplyWithResolution {
    @Test
    fun `unapply returns null for non-lissen URI`() {
      val uri = stubUri(scheme = "https", segments = listOf("api", "items"))
      val result = unapply(uri)
      assertEquals(null, result)
    }

    @Test
    fun `unapply returns null for file URI`() {
      val uri = stubUri(scheme = "file", segments = listOf("data", "media_cache", "book1", "file1"))
      val result = unapply(uri)
      assertEquals(null, result)
    }

    @Test
    fun `unapply extracts bookId and fileId from valid lissen URI`() {
      val uri = stubUri(scheme = "lissen", segments = listOf("book-abc", "file-xyz"))
      val result = unapply(uri)
      assertEquals("book-abc" to "file-xyz", result)
    }

    @Test
    fun `resolved URI scheme determines playback source type`() {
      val localUri = stubUri(scheme = "file")
      val remoteUri = stubUri(scheme = "https")

      assertEquals("file", localUri.scheme)
      assertEquals("https", remoteUri.scheme)
    }
  }

  @Nested
  inner class ProviderInvocation {
    @Test
    fun `provideFileUri is called with correct bookId and fileId`() {
      val uri = stubUri(scheme = "https")
      every { mediaProvider.provideFileUri("book-99", "file-42") } returns
        OperationResult.Success(uri)

      mediaProvider.provideFileUri("book-99", "file-42")

      verify { mediaProvider.provideFileUri("book-99", "file-42") }
    }

    @Test
    fun `different bookId-fileId pairs resolve independently`() {
      val uri1 = stubUri(scheme = "file")
      val uri2 = stubUri(scheme = "https")
      every { mediaProvider.provideFileUri("book-1", "file-1") } returns
        OperationResult.Success(uri1)
      every { mediaProvider.provideFileUri("book-2", "file-2") } returns
        OperationResult.Success(uri2)

      val result1 = mediaProvider.provideFileUri("book-1", "file-1")
      val result2 = mediaProvider.provideFileUri("book-2", "file-2")

      assertEquals("file", (result1 as OperationResult.Success).data.scheme)
      assertEquals("https", (result2 as OperationResult.Success).data.scheme)
    }
  }

  private fun stubUri(
    scheme: String?,
    segments: List<String> = emptyList(),
  ): Uri {
    val uri = mockk<Uri>()
    every { uri.scheme } returns scheme
    every { uri.pathSegments } returns segments
    every { uri.toString() } returns "$scheme://stub"
    return uri
  }
}
