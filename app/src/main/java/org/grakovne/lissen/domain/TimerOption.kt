package org.grakovne.lissen.domain

sealed interface TimerOption

class DurationTimerOption(
  val duration: Int,
) : TimerOption

data object CurrentEpisodeTimerOption : TimerOption
