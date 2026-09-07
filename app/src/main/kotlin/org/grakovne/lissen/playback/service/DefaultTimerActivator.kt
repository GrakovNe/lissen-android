package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTimerActivator
  @Inject
  constructor(
    private val preferences: PlaybackPreferences,
  ) {
    private var pending = true

    fun onPlaybackStarted(applyTimer: (TimerOption) -> Unit) {
      if (!pending) return
      pending = false
      preferences.getDefaultTimerOption()?.let { applyTimer(it) }
    }

    fun onTimerManuallySet() {
      pending = false
    }

    fun onTimerExpired() {
      pending = true
    }

    fun onNewBookPrepared() {
      pending = true
    }
  }
