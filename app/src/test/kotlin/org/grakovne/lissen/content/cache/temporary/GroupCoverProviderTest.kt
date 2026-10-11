package org.grakovne.lissen.content.cache.temporary

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.GroupCoverComposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class GroupCoverProviderTest {
  private val mediaProvider = mockk<LissenMediaProvider>()
  private val composer = mockk<GroupCoverComposer>()
  private val properties = mockk<ShortTermCacheStorageProperties>()
  private val provider = GroupCoverProvider(mediaProvider, composer, properties)

  @Test
  fun `returns the cached composite without fetching or recomposing`(
    @TempDir dir: File,
  ) = runBlocking {
    val cached = File(dir, "cached").apply { writeText("png") }
    every { properties.provideGroupCoverPath(any()) } returns cached

    val result = provider.provideGroupCover("ser-1", listOf("b1", "b2"))

    assertInstanceOf(OperationResult.Success::class.java, result)
    assertEquals(cached, (result as OperationResult.Success).data)
    coVerify(exactly = 0) { mediaProvider.fetchBookCover(any()) }
    verify(exactly = 0) { composer.compose(any()) }
  }

  @Test
  fun `composes from cover files and caches when no composite exists yet`(
    @TempDir dir: File,
  ) = runBlocking {
    val dest = File(dir, "composed")
    every { properties.provideGroupCoverPath(any()) } returns dest
    coEvery { mediaProvider.fetchBookCover("b1") } returns OperationResult.Success(File(dir, "b1"))
    coEvery { mediaProvider.fetchBookCover("b2") } returns OperationResult.Success(File(dir, "b2"))
    every { composer.compose(any()) } returns Buffer().apply { writeUtf8("PNGBYTES") }

    val result = provider.provideGroupCover("ser-1", listOf("b1", "b2"))

    assertInstanceOf(OperationResult.Success::class.java, result)
    assertEquals(dest, (result as OperationResult.Success).data)
    assertTrue(dest.exists())
    assertEquals("PNGBYTES", dest.readText())
    verify(exactly = 1) { composer.compose(any()) }
  }

  @Test
  fun `skips covers that fail to load and still composes from the rest`(
    @TempDir dir: File,
  ) = runBlocking {
    val dest = File(dir, "composed")
    every { properties.provideGroupCoverPath(any()) } returns dest
    coEvery { mediaProvider.fetchBookCover("b1") } returns OperationResult.Error(OperationError.NetworkError)
    coEvery { mediaProvider.fetchBookCover("b2") } returns OperationResult.Success(File(dir, "b2"))
    every { composer.compose(any()) } returns Buffer().apply { writeUtf8("PNG") }

    val result = provider.provideGroupCover("ser-1", listOf("b1", "b2"))

    assertInstanceOf(OperationResult.Success::class.java, result)
    verify(exactly = 1) { composer.compose(match { it.size == 1 }) }
  }

  @Test
  fun `returns error when nothing can be composed`(
    @TempDir dir: File,
  ) = runBlocking {
    every { properties.provideGroupCoverPath(any()) } returns File(dir, "missing")
    coEvery { mediaProvider.fetchBookCover(any()) } returns OperationResult.Error(OperationError.NetworkError)
    every { composer.compose(any()) } returns null

    val result = provider.provideGroupCover("ser-1", listOf("b1"))

    assertInstanceOf(OperationResult.Error::class.java, result)
    assertEquals(OperationError.InternalError, (result as OperationResult.Error).code)
  }
}
