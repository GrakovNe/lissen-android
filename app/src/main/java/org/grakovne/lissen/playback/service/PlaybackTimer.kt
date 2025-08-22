package org.grakovne.lissen.playback.service

import android.content.Context
import android.content.Intent
import android.os.CountDownTimer
import androidx.annotation.OptIn
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackTimer @Inject constructor(
  @ApplicationContext private val applicationContext: Context,
) {
  private val localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext)
  private var countDownTimer: CountDownTimer? = null
  
  fun startTimer(delayInSeconds: Double) {
    val delayInMillis = (delayInSeconds * 1000).toLong()
    if (delayInMillis <= 0L) return
    stopTimer()
    
    countDownTimer = object : CountDownTimer(delayInMillis, 500L - 1L) {
      
      @OptIn(UnstableApi::class)
      override fun onTick(millisUntilFinished: Long) {
        localBroadcastManager.sendBroadcast(
          Intent(PlaybackService.TIMER_TICK).putExtra(PlaybackService.TIMER_REMAINING, millisUntilFinished),
        )
      }
      
      @OptIn(UnstableApi::class)
      override fun onFinish() {
        localBroadcastManager.sendBroadcast(Intent(PlaybackService.TIMER_EXPIRED))
      }
    }.also { it.start() }
  }
  
  fun stopTimer() {
    countDownTimer?.cancel()
    countDownTimer = null
  }
}
