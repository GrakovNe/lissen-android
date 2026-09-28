package org.grakovne.lissen.playback.service

import android.os.CountDownTimer

/** What the playback timer needs of a countdown; lets tests stand in for the Android one. */
internal interface Countdown {
  fun stop()

  fun pause(): Long

  fun resume(): Countdown
}

/** Creates and starts a countdown; a test hands in one that does not need a looper. */
internal fun interface CountdownFactory {
  fun create(
    totalMillis: Long,
    intervalMillis: Long,
    onTickSeconds: (Long) -> Unit,
    onFinished: () -> Unit,
  ): Countdown
}

internal class SuspendableCountDownTimer(
  totalMillis: Long,
  private val intervalMillis: Long,
  private val onTickSeconds: (Long) -> Unit,
  private val onFinished: () -> Unit,
) : CountDownTimer(totalMillis, intervalMillis),
  Countdown {
  private var remainingMillis: Long = totalMillis

  override fun stop() = cancel()

  override fun pause(): Long {
    cancel()
    return remainingMillis
  }

  override fun resume(): Countdown {
    val timer = SuspendableCountDownTimer(remainingMillis, intervalMillis, onTickSeconds, onFinished)
    timer.start()

    return timer
  }

  override fun onTick(millisUntilFinished: Long) {
    remainingMillis = millisUntilFinished
    onTickSeconds(millisUntilFinished / 1000)
  }

  override fun onFinish() {
    remainingMillis = 0L
    onFinished()
  }
}
