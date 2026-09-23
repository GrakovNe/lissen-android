package org.grakovne.lissen.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.domain.EqualizerSettings
import org.grakovne.lissen.domain.SeekTime
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.EqualizerBandProvider
import org.grakovne.lissen.playback.EqualizerCapabilities
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PlaybackSettingsViewModel
  @Inject
  constructor(
    private val playback: PlaybackPreferences,
    private val equalizerBandProvider: EqualizerBandProvider,
  ) : ViewModel() {
    private val _preferredPlaybackVolumeBoost = MutableStateFlow(playback.getPlaybackVolumeBoost())
    val preferredPlaybackVolumeBoost: StateFlow<Int> = _preferredPlaybackVolumeBoost.asStateFlow()

    private val _equalizer = MutableStateFlow(playback.getEqualizer())
    val equalizer: StateFlow<EqualizerSettings> = _equalizer.asStateFlow()

    val equalizerCapabilities: StateFlow<EqualizerCapabilities?> =
      flow { emit(equalizerBandProvider.getCapabilities()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _seekTime = MutableStateFlow(playback.getSeekTime())
    val seekTime: StateFlow<SeekTime> = _seekTime.asStateFlow()

    private val _defaultTimerOption = MutableStateFlow<TimerOption?>(playback.getDefaultTimerOption())
    val defaultTimerOption: StateFlow<TimerOption?> = _defaultTimerOption.asStateFlow()

    private val _sleepTimerFadeEnabled = MutableStateFlow(playback.getSleepTimerSettings().fadeEnabled)
    val sleepTimerFadeEnabled: StateFlow<Boolean> = _sleepTimerFadeEnabled.asStateFlow()

    private val _sleepTimerFadeSeconds = MutableStateFlow(playback.getSleepTimerSettings().fadeSeconds)
    val sleepTimerFadeSeconds: StateFlow<Int> = _sleepTimerFadeSeconds.asStateFlow()

    private val _softwareCodecsEnabled = MutableStateFlow(playback.getSoftwareCodecsEnabled())
    val softwareCodecsEnabled: StateFlow<Boolean> = _softwareCodecsEnabled.asStateFlow()
    val softwareCodecsEnabledOnStart: Boolean = playback.getSoftwareCodecsEnabled()

    private val _audioFocusLossPolicy = MutableStateFlow(playback.getAudioFocusLossPolicy())
    val audioFocusLossPolicy: StateFlow<AudioFocusLossPolicy> = _audioFocusLossPolicy.asStateFlow()

    fun preferPlaybackVolumeBoost(db: Int) {
      Timber.d("User action: preferPlaybackVolumeBoost $db dB")
      _preferredPlaybackVolumeBoost.value = db
      playback.savePlaybackVolumeBoost(db)
    }

    fun preferEqualizerGain(
      band: Int,
      db: Int,
    ) {
      Timber.d("User action: preferEqualizerGain band=$band $db dB")
      val current = _equalizer.value
      val size = maxOf(current.gains.size, band + 1)
      val gains = List(size) { index -> if (index == band) db else current.gains.getOrElse(index) { 0 } }

      saveEqualizer(current.copy(gains = gains))
    }

    fun resetEqualizer() {
      Timber.d("User action: resetEqualizer")
      saveEqualizer(_equalizer.value.copy(gains = emptyList()))
    }

    private fun saveEqualizer(settings: EqualizerSettings) {
      _equalizer.value = settings
      playback.saveEqualizer(settings)
    }

    fun preferForward(seconds: Int) {
      Timber.d("User action: preferForward $seconds")
      saveSeekTime(_seekTime.value.copy(forward = seconds))
    }

    fun preferRewind(seconds: Int) {
      Timber.d("User action: preferRewind $seconds")
      saveSeekTime(_seekTime.value.copy(rewind = seconds))
    }

    private fun saveSeekTime(seekTime: SeekTime) {
      playback.saveSeekTime(seekTime)
      _seekTime.value = seekTime
    }

    fun saveDefaultTimerOption(option: TimerOption?) {
      Timber.d("User action: saveDefaultTimerOption option=$option")
      _defaultTimerOption.value = option
      playback.saveDefaultTimerOption(option)
    }

    fun preferSleepTimerFadeEnabled(value: Boolean) {
      Timber.d("User action: preferSleepTimerFadeEnabled $value")
      _sleepTimerFadeEnabled.value = value
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(fadeEnabled = value))
    }

    fun preferSleepTimerFadeSeconds(seconds: Int) {
      Timber.d("User action: preferSleepTimerFadeSeconds $seconds")
      _sleepTimerFadeSeconds.value = seconds
      playback.saveSleepTimerSettings(playback.getSleepTimerSettings().copy(fadeSeconds = seconds))
    }

    fun preferSoftwareCodecsEnabled(value: Boolean) {
      Timber.d("User action: preferSoftwareCodecsEnabled $value")
      _softwareCodecsEnabled.value = value
      playback.saveSoftwareCodecsEnabled(value)
    }

    fun preferAudioFocusLossPolicy(policy: AudioFocusLossPolicy) {
      Timber.d("User action: preferAudioFocusLossPolicy $policy")
      _audioFocusLossPolicy.value = policy
      playback.saveAudioFocusLossPolicy(policy)
    }
  }
