package org.grakovne.lissen.widget

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetPlaybackControllerThreadingTest {
  @Test
  fun `widget preparation and action run on the main thread`() {
    val mainDispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-main") }.asCoroutineDispatcher()
    val workerDispatcher = Executors.newSingleThreadExecutor { Thread(it, "test-worker") }.asCoroutineDispatcher()

    try {
      Dispatchers.setMain(mainDispatcher)

      val mainThread = runBlocking(mainDispatcher) { Thread.currentThread() }
      val clearThread = AtomicReference<Thread?>()
      val prepareThread = AtomicReference<Thread?>()
      val actionThread = AtomicReference<Thread?>()
      val book = mockk<DetailedItem> { every { id } returns "book-1" }
      val playingBook = MutableStateFlow<DetailedItem?>(null)
      val playbackReady = MutableStateFlow(false)
      val preparingError = MutableStateFlow(false)
      val repository = mockk<MediaRepository>(relaxed = true)

      every { repository.playingBook } returns playingBook
      every { repository.isPlaybackReady } returns playbackReady
      every { repository.mediaPreparingError } returns preparingError
      every { repository.clearPreparedItem() } answers { clearThread.set(Thread.currentThread()) }
      coEvery { repository.preparePlayback("book-1", null) } coAnswers {
        prepareThread.set(Thread.currentThread())
        playingBook.value = book
        playbackReady.value = true
        true
      }

      val controller = WidgetPlaybackController(repository, mockk<PlaybackPreferences>(relaxed = true))

      runBlocking(workerDispatcher) {
        controller.runForItem("book-1") { actionThread.set(Thread.currentThread()) }
      }

      assertSame(mainThread, clearThread.get())
      assertSame(mainThread, prepareThread.get())
      assertSame(mainThread, actionThread.get())
    } finally {
      Dispatchers.resetMain()
      mainDispatcher.close()
      workerDispatcher.close()
    }
  }
}
