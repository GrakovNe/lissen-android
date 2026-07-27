package org.grakovne.lissen.content

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.EntryPointAccessors
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExternalCoverProviderTest {
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val resources = mockk<Resources>()
  private val context =
    mockk<Context> {
      every { applicationContext } returns this@mockk
      every { resources } returns this@ExternalCoverProviderTest.resources
    }

  private lateinit var provider: ExternalCoverProvider

  @BeforeEach
  fun setUp() {
    mockkStatic(EntryPointAccessors::class)
    every {
      EntryPointAccessors.fromApplication(context, LissenMediaProviderEntryPoint::class.java)
    } returns
      mockk<LissenMediaProviderEntryPoint> {
        every { getLissenMediaProvider() } returns mediaProvider
      }

    provider = spyk(ExternalCoverProvider())
    every { provider.context } returns context
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `serves the descriptor of the file returned by fetchBookCover`(
    @TempDir dir: File,
  ) {
    val coverFile = File(dir, "0f1e2d-hashed-name").apply { writeText("png") }
    coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Success(coverFile)

    mockkStatic(ParcelFileDescriptor::class)
    val descriptor = mockk<ParcelFileDescriptor>()
    every { ParcelFileDescriptor.open(coverFile, ParcelFileDescriptor.MODE_READ_ONLY) } returns descriptor
    mockkConstructor(AssetFileDescriptor::class)

    val result = provider.openAssetFile(coverUri("book-1"), "r")

    assertNotNull(result)
    verify(exactly = 1) { ParcelFileDescriptor.open(coverFile, ParcelFileDescriptor.MODE_READ_ONLY) }
  }

  @Test
  fun `serves the fallback cover when fetch fails`() {
    coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Error(OperationError.NetworkError)

    val fallback = mockk<AssetFileDescriptor>()
    every { resources.openRawResourceFd(R.raw.cover_fallback_png) } returns fallback

    mockkStatic(ParcelFileDescriptor::class)

    val result = provider.openAssetFile(coverUri("book-1"), "r")

    assertSame(fallback, result)
    verify(exactly = 0) { ParcelFileDescriptor.open(any<File>(), any<Int>()) }
  }

  @Test
  fun `fetches the cover for the book id from the uri`(
    @TempDir dir: File,
  ) {
    val coverFile = File(dir, "cover").apply { writeText("png") }
    coEvery { mediaProvider.fetchBookCover(any()) } returns OperationResult.Success(coverFile)

    mockkStatic(ParcelFileDescriptor::class)
    every { ParcelFileDescriptor.open(any(), any()) } returns mockk()
    mockkConstructor(AssetFileDescriptor::class)

    provider.openAssetFile(coverUri("podcast-42"), "r")

    coVerify(exactly = 1) { mediaProvider.fetchBookCover("podcast-42") }
  }

  private fun coverUri(bookId: String): Uri =
    mockk {
      every { lastPathSegment } returns bookId
    }
}
