package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DetailedItem.Companion.same
import org.grakovne.lissen.domain.MediaProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MediaPlaybackTransportTest {
  private class FakeController {
    val calls = mutableListOf<String>()
    var speed: Float? = null
      private set

    fun prepare() = calls.add("prepare").let { }

    fun setPlaybackSpeed(value: Float) {
      speed = value
      calls.add("setPlaybackSpeed")
    }

    fun play() = calls.add("play").let { }

    fun pause() = calls.add("pause").let { }

    fun stop() = calls.add("stop").let { }

    fun clearMediaItems() = calls.add("clearMediaItems").let { }
  }

  private fun startPreparingPlayback(
    book: DetailedItem,
    playingBook: MutableStateFlow<DetailedItem?>,
    totalPosition: MutableStateFlow<Double>,
    isPlaying: MutableStateFlow<Boolean>,
    savedItems: MutableList<DetailedItem>,
    commands: MutableList<PlaybackCommand>,
  ) {
    val sameBook = playingBook.value?.same(book) ?: false

    if (sameBook.not()) {
      totalPosition.value = 0.0
      isPlaying.value = false

      playingBook.value = book
      savedItems.add(book)

      commands.add(PlaybackCommand.PreparePlayback)
    }
  }

  private fun play(
    controller: FakeController?,
    playbackSpeed: Float,
  ) {
    if (controller == null) return

    controller.prepare()
    controller.setPlaybackSpeed(playbackSpeed)
    controller.play()
  }

  private fun pause(controller: FakeController?) {
    if (controller != null) {
      controller.pause()
    }
  }

  private fun clearPlayingBook(
    controller: FakeController?,
    isPlaying: MutableStateFlow<Boolean>,
    isPlaybackReady: MutableStateFlow<Boolean>,
    playingBook: MutableStateFlow<DetailedItem?>,
    clearedItems: MutableList<Unit>,
  ) {
    if (controller != null) {
      controller.stop()
      controller.clearMediaItems()
    }

    isPlaying.value = false
    isPlaybackReady.value = false
    playingBook.value = null
    clearedItems.add(Unit)
  }

  @Nested
  inner class StartPreparingPlayback {
    @Test
    fun `new book resets state and enqueues exactly one PreparePlayback`() {
      val playingBook = MutableStateFlow<DetailedItem?>(null)
      val totalPosition = MutableStateFlow(42.0)
      val isPlaying = MutableStateFlow(true)
      val saved = mutableListOf<DetailedItem>()
      val commands = mutableListOf<PlaybackCommand>()

      val book = detailedItem("book-1")
      startPreparingPlayback(book, playingBook, totalPosition, isPlaying, saved, commands)

      assertEquals(listOf<PlaybackCommand>(PlaybackCommand.PreparePlayback), commands)
      assertEquals(0.0, totalPosition.value)
      assertEquals(false, isPlaying.value)
      assertEquals(book, playingBook.value)
      assertEquals(listOf(book), saved)
    }

    @Test
    fun `same book (only progress differs) is a no-op`() {
      val current = detailedItem("book-1", progress = MediaProgress(10.0, false, 1))
      val playingBook = MutableStateFlow<DetailedItem?>(current)
      val totalPosition = MutableStateFlow(42.0)
      val isPlaying = MutableStateFlow(true)
      val saved = mutableListOf<DetailedItem>()
      val commands = mutableListOf<PlaybackCommand>()

      val reopened = detailedItem("book-1", progress = MediaProgress(99.0, false, 2))
      startPreparingPlayback(reopened, playingBook, totalPosition, isPlaying, saved, commands)

      assertTrue(commands.isEmpty())
      assertEquals(42.0, totalPosition.value)
      assertEquals(true, isPlaying.value)
      assertEquals(current, playingBook.value)
      assertTrue(saved.isEmpty())
    }

    @Test
    fun `switching to a different book prepares the new one`() {
      val playingBook = MutableStateFlow<DetailedItem?>(detailedItem("book-1"))
      val totalPosition = MutableStateFlow(42.0)
      val isPlaying = MutableStateFlow(true)
      val saved = mutableListOf<DetailedItem>()
      val commands = mutableListOf<PlaybackCommand>()

      val next = detailedItem("book-2")
      startPreparingPlayback(next, playingBook, totalPosition, isPlaying, saved, commands)

      assertEquals(listOf<PlaybackCommand>(PlaybackCommand.PreparePlayback), commands)
      assertEquals(next, playingBook.value)
      assertEquals(0.0, totalPosition.value)
    }
  }

  @Nested
  inner class PlayRouting {
    @Test
    fun `play drives the controller in order with the saved speed`() {
      val controller = FakeController()

      play(controller, playbackSpeed = 1.5f)

      assertEquals(listOf("prepare", "setPlaybackSpeed", "play"), controller.calls)
      assertEquals(1.5f, controller.speed)
    }

    @Test
    fun `play before the controller is connected is a safe no-op`() {
      play(controller = null, playbackSpeed = 1.0f)
    }
  }

  @Nested
  inner class PauseRouting {
    @Test
    fun `pause drives the controller`() {
      val controller = FakeController()

      pause(controller)

      assertEquals(listOf("pause"), controller.calls)
    }

    @Test
    fun `pause before the controller is connected is a safe no-op`() {
      pause(controller = null)
    }
  }

  @Nested
  inner class ClearPlayingBook {
    @Test
    fun `clear stops and empties the controller queue and resets state`() {
      val controller = FakeController()
      val isPlaying = MutableStateFlow(true)
      val isPlaybackReady = MutableStateFlow(true)
      val playingBook = MutableStateFlow<DetailedItem?>(detailedItem("book-1"))
      val cleared = mutableListOf<Unit>()

      clearPlayingBook(controller, isPlaying, isPlaybackReady, playingBook, cleared)

      assertEquals(listOf("stop", "clearMediaItems"), controller.calls)
      assertEquals(false, isPlaying.value)
      assertEquals(false, isPlaybackReady.value)
      assertNull(playingBook.value)
      assertEquals(1, cleared.size)
    }

    @Test
    fun `clear without a controller still resets state`() {
      val isPlaying = MutableStateFlow(true)
      val isPlaybackReady = MutableStateFlow(true)
      val playingBook = MutableStateFlow<DetailedItem?>(detailedItem("book-1"))
      val cleared = mutableListOf<Unit>()

      clearPlayingBook(null, isPlaying, isPlaybackReady, playingBook, cleared)

      assertEquals(false, isPlaying.value)
      assertEquals(false, isPlaybackReady.value)
      assertNull(playingBook.value)
      assertEquals(1, cleared.size)
    }
  }

  private fun detailedItem(
    id: String,
    progress: MediaProgress? = null,
  ) = DetailedItem(
    id = id,
    title = "title",
    subtitle = null,
    author = null,
    narrator = null,
    publisher = null,
    series = emptyList(),
    year = null,
    abstract = null,
    files = emptyList(),
    chapters = emptyList(),
    progress = progress,
    libraryId = null,
    localProvided = false,
    createdAt = 0,
    updatedAt = 0,
  )
}
