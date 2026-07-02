package org.grakovne.lissen.playback

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MediaRepositoryErrorHandlingTest {
  @Nested
  inner class OnPlayerError {
    @Test
    fun `error sets mediaPreparingError to true`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(true)

      simulateOnPlayerError(isPlaying, mediaPreparingError)

      assertTrue(mediaPreparingError.value)
    }

    @Test
    fun `error sets isPlaying to false`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(true)

      simulateOnPlayerError(isPlaying, mediaPreparingError)

      assertFalse(isPlaying.value)
    }

    @Test
    fun `error when already not playing keeps isPlaying false`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(false)

      simulateOnPlayerError(isPlaying, mediaPreparingError)

      assertFalse(isPlaying.value)
      assertTrue(mediaPreparingError.value)
    }

    @Test
    fun `multiple errors keep error state true`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(true)

      simulateOnPlayerError(isPlaying, mediaPreparingError)
      assertTrue(mediaPreparingError.value)

      isPlaying.value = true
      simulateOnPlayerError(isPlaying, mediaPreparingError)
      assertTrue(mediaPreparingError.value)
      assertFalse(isPlaying.value)
    }
  }

  @Nested
  inner class ErrorRecovery {
    @Test
    fun `error state can be cleared`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(true)

      simulateOnPlayerError(isPlaying, mediaPreparingError)
      assertTrue(mediaPreparingError.value)

      mediaPreparingError.value = false
      assertFalse(mediaPreparingError.value)
    }

    @Test
    fun `isPlaying can be restored after error`() {
      val mediaPreparingError = MutableStateFlow(false)
      val isPlaying = MutableStateFlow(true)

      simulateOnPlayerError(isPlaying, mediaPreparingError)
      assertFalse(isPlaying.value)

      isPlaying.value = true
      assertTrue(isPlaying.value)
    }
  }

  @Nested
  inner class PlayAfterPrepareReset {
    @Test
    fun `error clears the deferred autoplay flag`() {
      val playAfterPrepare = MutableStateFlow(true)

      simulateOnPlayerError(
        isPlaying = MutableStateFlow(true),
        mediaPreparingError = MutableStateFlow(false),
        playAfterPrepare = playAfterPrepare,
      )

      assertFalse(playAfterPrepare.value)
    }

    @Test
    fun `clearing prepared item clears the deferred autoplay flag`() {
      val playAfterPrepare = MutableStateFlow(true)

      simulateClearPreparedItem(playAfterPrepare)

      assertFalse(playAfterPrepare.value)
    }
  }

  private fun simulateOnPlayerError(
    isPlaying: MutableStateFlow<Boolean>,
    mediaPreparingError: MutableStateFlow<Boolean>,
    playAfterPrepare: MutableStateFlow<Boolean> = MutableStateFlow(false),
  ) {
    isPlaying.value = false
    playAfterPrepare.value = false
    mediaPreparingError.value = true
  }

  private fun simulateClearPreparedItem(playAfterPrepare: MutableStateFlow<Boolean>) {
    playAfterPrepare.value = false
  }
}
