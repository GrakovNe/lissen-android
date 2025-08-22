package org.grakovne.lissen.playback.service

import android.os.CountDownTimer

class PausableCountDownTimer(
  private val totalMillis: Long,
  private val intervalMillis: Long,
  private val onTickSeconds: (Long) -> Unit,
  private val onFinished: () -> Unit,
) : CountDownTimer(totalMillis, intervalMillis) {
  
  private var remainingMillis: Long = totalMillis
  
  override fun onTick(millisUntilFinished: Long) {
    remainingMillis = millisUntilFinished
    onTickSeconds(millisUntilFinished / 1000)
  }
  
  override fun onFinish() {
    remainingMillis = 0L
    onFinished()
  }
  
  fun pause(): Long {
    cancel()
    return remainingMillis
  }
  
  fun resume(): PausableCountDownTimer {
    val timer = PausableCountDownTimer(remainingMillis, intervalMillis, onTickSeconds, onFinished)
    timer.start()
    
    return timer
  }
}
