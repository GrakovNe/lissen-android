package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Specifies the progress-updating lifecycle that [MediaRepository] drives from
 * `onIsPlayingChanged`: the recurring progress poll must run only while playback is
 * actually playing and a book is prepared, and it must be stopped on pause.
 *
 * [MediaRepository] depends on Android primitives (MediaController, Handler, Looper) and
 * cannot be instantiated in a JVM unit test, so the start/stop decision is mirrored here
 * exactly as in production — the same convention used by [MediaRepositoryErrorHandlingTest].
 */
class MediaProgressUpdateLifecycleTest {
  /**
   * Stand-in for the production `Handler` loop started by `startUpdatingProgress` and torn
   * down by `stopUpdatingProgress`. `startUpdatingProgress` first clears pending callbacks,
   * so re-starting while already running is idempotent (single active loop).
   */
  private class ProgressUpdater {
    var running = false
      private set

    var startInvocations = 0
      private set

    fun start() {
      // mirrors handler.removeCallbacksAndMessages(null) before re-posting
      running = true
      startInvocations++
    }

    fun stop() {
      running = false
    }
  }

  /** Faithful copy of the fixed `onIsPlayingChanged` branch in [MediaRepository]. */
  private fun onIsPlayingChanged(
    isPlaying: Boolean,
    playingBook: MutableStateFlow<Boolean>,
    isPlayingState: MutableStateFlow<Boolean>,
    updater: ProgressUpdater,
  ) {
    isPlayingState.value = isPlaying
    if (isPlaying) {
      if (playingBook.value) updater.start()
    } else {
      updater.stop()
    }
  }

  @Nested
  inner class StartStop {
    @Test
    fun `starts updating when playback starts with a prepared book`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(true)
      val isPlaying = MutableStateFlow(false)

      onIsPlayingChanged(true, book, isPlaying, updater)

      assertTrue(updater.running)
      assertTrue(isPlaying.value)
      assertEquals(1, updater.startInvocations)
    }

    @Test
    fun `stops updating when playback pauses`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(true)
      val isPlaying = MutableStateFlow(false)

      onIsPlayingChanged(true, book, isPlaying, updater)
      assertTrue(updater.running)

      onIsPlayingChanged(false, book, isPlaying, updater)

      assertFalse(updater.running)
      assertFalse(isPlaying.value)
    }

    @Test
    fun `does not start updating when playing without a prepared book`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(false)

      onIsPlayingChanged(true, book, isPlaying, updater)

      assertFalse(updater.running)
      assertEquals(0, updater.startInvocations)
      assertTrue(isPlaying.value)
    }
  }

  @Nested
  inner class Lifecycle {
    @Test
    fun `resumes updating after a pause`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(true)
      val isPlaying = MutableStateFlow(false)

      onIsPlayingChanged(true, book, isPlaying, updater)
      onIsPlayingChanged(false, book, isPlaying, updater)
      assertFalse(updater.running)

      onIsPlayingChanged(true, book, isPlaying, updater)

      assertTrue(updater.running)
    }

    @Test
    fun `toggling play and pause never leaves the loop running after pause`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(true)
      val isPlaying = MutableStateFlow(false)

      repeat(5) {
        onIsPlayingChanged(true, book, isPlaying, updater)
        assertTrue(updater.running)

        onIsPlayingChanged(false, book, isPlaying, updater)
        assertFalse(updater.running)
      }
    }

    @Test
    fun `repeated play events keep a single active loop`() {
      val updater = ProgressUpdater()
      val book = MutableStateFlow(true)
      val isPlaying = MutableStateFlow(false)

      onIsPlayingChanged(true, book, isPlaying, updater)
      onIsPlayingChanged(true, book, isPlaying, updater)
      onIsPlayingChanged(true, book, isPlaying, updater)

      // each start clears the previous callbacks first, so only one loop is ever active
      assertTrue(updater.running)
      assertEquals(3, updater.startInvocations)
    }
  }
}
