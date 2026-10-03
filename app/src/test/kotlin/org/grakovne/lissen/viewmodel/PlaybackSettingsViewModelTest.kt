package org.grakovne.lissen.viewmodel

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.RewindOnPauseSettings
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.SleepTimerSettings
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.EqualizerBandProvider
import org.grakovne.lissen.playback.EqualizerCapabilities
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsViewModelTest {
  private val playback = mockk<PlaybackPreferences>(relaxed = true)
  private val equalizerBandProvider = mockk<EqualizerBandProvider>(relaxed = true)
  private lateinit var viewModel: PlaybackSettingsViewModel

  @BeforeEach
  fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())

    every { playback.getPlaybackVolumeBoost() } returns 0
    every { playback.getSeekTime() } returns SeekTime.Default
    every { playback.getSleepTimerSettings() } returns SleepTimerSettings.Default
    every { playback.getRewindOnPause() } returns RewindOnPauseSettings.Default
    every { playback.getEqualizer() } returns EqualizerSettings.Default
    every { playback.getSoftwareCodecsEnabled() } returns false
    coEvery { equalizerBandProvider.getCapabilities() } returns EqualizerCapabilities.Unavailable

    viewModel = PlaybackSettingsViewModel(playback, equalizerBandProvider)
  }

  @AfterEach
  fun teardown() {
    Dispatchers.resetMain()
  }

  @Nested
  inner class VolumeBoost {
    @Test
    fun `preferPlaybackVolumeBoost updates StateFlow`() {
      viewModel.preferPlaybackVolumeBoost(12)
      assertEquals(12, viewModel.preferredPlaybackVolumeBoost.value)
    }

    @Test
    fun `preferPlaybackVolumeBoost saves to preferences`() {
      viewModel.preferPlaybackVolumeBoost(6)
      verify { playback.savePlaybackVolumeBoost(6) }
    }
  }

  @Nested
  inner class Equalizer {
    @Test
    fun `preferEqualizerGain grows the gains up to the band and persists`() {
      viewModel.preferEqualizerGain(band = 2, db = 3)

      assertEquals(listOf(0, 0, 3), viewModel.equalizer.value.gains)
      verify { playback.saveEqualizer(EqualizerSettings.Default.copy(gains = listOf(0, 0, 3))) }
    }

    @Test
    fun `resetEqualizer clears the gains and persists`() {
      viewModel.preferEqualizerGain(band = 0, db = 5)

      viewModel.resetEqualizer()

      assertTrue(
        viewModel.equalizer.value.gains
          .isEmpty(),
      )
      verify { playback.saveEqualizer(EqualizerSettings.Default.copy(gains = emptyList())) }
    }
  }

  @Nested
  inner class SeekTimePreference {
    @Test
    fun `preferForward updates seek forward`() {
      viewModel.preferForward(60)
      assertEquals(60, viewModel.seekTime.value.forward)
    }

    @Test
    fun `preferRewind updates seek rewind`() {
      viewModel.preferRewind(30)
      assertEquals(30, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferForward preserves rewind value`() {
      viewModel.preferForward(60)
      assertEquals(SeekTime.Default.rewind, viewModel.seekTime.value.rewind)
    }

    @Test
    fun `preferRewind preserves forward value`() {
      viewModel.preferRewind(10)
      assertEquals(SeekTime.Default.forward, viewModel.seekTime.value.forward)
    }
  }

  @Nested
  inner class RewindOnPausePreference {
    @Test
    fun `rewind on pause is initialized from preferences`() {
      every { playback.getRewindOnPause() } returns RewindOnPauseSettings(enabled = true, seconds = 10)

      assertEquals(10, PlaybackSettingsViewModel(playback, equalizerBandProvider).rewindOnPause.value)
    }

    @Test
    fun `a stored rewind that is off reads as none`() {
      assertNull(viewModel.rewindOnPause.value)
    }

    @Test
    fun `preferRewindOnPause switches on with the seconds and persists`() {
      viewModel.preferRewindOnPause(10)

      assertEquals(10, viewModel.rewindOnPause.value)
      verify { playback.saveRewindOnPause(RewindOnPauseSettings(enabled = true, seconds = 10)) }
    }

    @Test
    fun `preferRewindOnPause off persists as disabled`() {
      every { playback.getRewindOnPause() } returns RewindOnPauseSettings(enabled = true, seconds = 10)

      viewModel.preferRewindOnPause(null)

      assertNull(viewModel.rewindOnPause.value)
      verify { playback.saveRewindOnPause(RewindOnPauseSettings(enabled = false, seconds = 10)) }
    }
  }

  @Nested
  inner class SleepTimerFadePreference {
    @Test
    fun `fade state is initialized from preferences`() {
      every { playback.getSleepTimerSettings() } returns SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45)

      assertEquals(45, PlaybackSettingsViewModel(playback, equalizerBandProvider).sleepTimerFade.value)
    }

    @Test
    fun `a stored fade that is off reads as none`() {
      assertNull(viewModel.sleepTimerFade.value)
    }

    @Test
    fun `preferSleepTimerFade switches on with the seconds and persists`() {
      viewModel.preferSleepTimerFade(45)

      assertEquals(45, viewModel.sleepTimerFade.value)
      verify { playback.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45)) }
    }

    @Test
    fun `preferSleepTimerFade off persists as disabled`() {
      every { playback.getSleepTimerSettings() } returns SleepTimerSettings(fadeEnabled = true, fadeSeconds = 45)

      viewModel.preferSleepTimerFade(null)

      assertNull(viewModel.sleepTimerFade.value)
      verify { playback.saveSleepTimerSettings(SleepTimerSettings(fadeEnabled = false, fadeSeconds = 45)) }
    }
  }

  @Nested
  inner class Toggles {
    @Test
    fun `saveDefaultTimerOption updates StateFlow and preferences`() {
      val option = DurationTimerOption(600)

      viewModel.saveDefaultTimerOption(option)

      assertEquals(option, viewModel.defaultTimerOption.value)
      verify { playback.saveDefaultTimerOption(option) }
    }

    @Test
    fun `preferAudioFocusLossPolicy updates StateFlow and preferences`() {
      viewModel.preferAudioFocusLossPolicy(AudioFocusLossPolicy.LOWER_VOLUME)

      assertEquals(AudioFocusLossPolicy.LOWER_VOLUME, viewModel.audioFocusLossPolicy.value)
      verify { playback.saveAudioFocusLossPolicy(AudioFocusLossPolicy.LOWER_VOLUME) }
    }

    @Test
    fun `preferSoftwareCodecsEnabled updates StateFlow and preferences`() {
      viewModel.preferSoftwareCodecsEnabled(true)

      assertTrue(viewModel.softwareCodecsEnabled.value)
      assertFalse(viewModel.softwareCodecsEnabledOnStart)
      verify { playback.saveSoftwareCodecsEnabled(true) }
    }
  }
}
