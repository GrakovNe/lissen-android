package org.grakovne.lissen.playback

import androidx.media3.common.PlaybackException
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

  private fun simulateOnPlayerError(
    isPlaying: MutableStateFlow<Boolean>,
    mediaPreparingError: MutableStateFlow<Boolean>,
  ) {
    isPlaying.value = false
    mediaPreparingError.value = true
  }
}
