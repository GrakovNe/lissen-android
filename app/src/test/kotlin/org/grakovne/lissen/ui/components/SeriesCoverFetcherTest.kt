package org.grakovne.lissen.ui.components

import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.cache.temporary.SeriesCoverProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SeriesCoverFetcherTest {
  private val options = mockk<Options>(relaxed = true)

  @Test
  fun `keyer encodes series id and ordered cover ids`() {
    assertEquals(
      "series:s1:a,b,c",
      SeriesCoverKeyer().key(SeriesCoverKey("s1", listOf("a", "b", "c")), options),
    )
  }

  @Test
  fun `keyer key changes when cover set or order changes`() {
    val keyer = SeriesCoverKeyer()
    val base = keyer.key(SeriesCoverKey("s1", listOf("a", "b")), options)

    assertNotEquals(base, keyer.key(SeriesCoverKey("s1", listOf("b", "a")), options))
    assertNotEquals(base, keyer.key(SeriesCoverKey("s1", listOf("a", "b", "c")), options))
  }

  @Test
  fun `fetch wraps the composed file as a disk source`(
    @TempDir dir: File,
  ) = runBlocking {
    val file = File(dir, "composite").apply { writeText("png") }
    val provider = mockk<SeriesCoverProvider>()
    coEvery { provider.provideSeriesCover("s1", listOf("a")) } returns OperationResult.Success(file)

    val result = SeriesCoverFetcher(provider, SeriesCoverKey("s1", listOf("a"))).fetch()

    assertInstanceOf(SourceFetchResult::class.java, result)
    assertEquals(DataSource.DISK, (result as SourceFetchResult).dataSource)
  }

  @Test
  fun `fetch returns null on provider error`() =
    runBlocking {
      val provider = mockk<SeriesCoverProvider>()
      coEvery { provider.provideSeriesCover(any(), any()) } returns OperationResult.Error(OperationError.InternalError)

      val result = SeriesCoverFetcher(provider, SeriesCoverKey("s1", listOf("a"))).fetch()

      assertNull(result)
    }
}
