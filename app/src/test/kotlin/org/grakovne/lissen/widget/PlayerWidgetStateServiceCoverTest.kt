package org.grakovne.lissen.widget

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.grakovne.lissen.channel.common.OperationError
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class PlayerWidgetStateServiceCoverTest {
  private val context = mockk<Context>(relaxed = true)
  private val mediaRepository = mockk<MediaRepository>(relaxed = true)
  private val mediaProvider = mockk<LissenMediaProvider>()

  private val service = PlayerWidgetStateService(context, mediaRepository, mediaProvider)

  @Test
  fun `cover is fetched once per book across repeated state updates`() =
    runTest {
      val cover = File("cover-1")
      coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Success(cover)

      assertEquals(cover, service.provideCover("book-1"))
      assertEquals(cover, service.provideCover("book-1"))
      assertEquals(cover, service.provideCover("book-1"))

      coVerify(exactly = 1) { mediaProvider.fetchBookCover("book-1") }
    }

  @Test
  fun `switching book fetches the new cover`() =
    runTest {
      val firstCover = File("cover-1")
      val secondCover = File("cover-2")
      coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Success(firstCover)
      coEvery { mediaProvider.fetchBookCover("book-2") } returns OperationResult.Success(secondCover)

      assertEquals(firstCover, service.provideCover("book-1"))
      assertEquals(secondCover, service.provideCover("book-2"))

      coVerify(exactly = 1) { mediaProvider.fetchBookCover("book-1") }
      coVerify(exactly = 1) { mediaProvider.fetchBookCover("book-2") }
    }

  @Test
  fun `failed fetch is not cached and is retried`() =
    runTest {
      coEvery { mediaProvider.fetchBookCover("book-1") } returns OperationResult.Error(OperationError.NetworkError)

      assertNull(service.provideCover("book-1"))
      assertNull(service.provideCover("book-1"))

      coVerify(exactly = 2) { mediaProvider.fetchBookCover("book-1") }
    }
}
