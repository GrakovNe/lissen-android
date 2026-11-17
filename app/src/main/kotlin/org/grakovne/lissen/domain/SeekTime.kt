package org.grakovne.lissen.domain

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import kotlin.math.abs

@Keep
@JsonClass(generateAdapter = true)
data class SeekTime(
  val rewind: Int,
  val forward: Int,
) {
  /** Snaps both intervals onto [ALLOWED_SECONDS], so values saved by older versions still match an icon. */
  fun normalized(): SeekTime = SeekTime(rewind = snap(rewind), forward = snap(forward))

  companion object {
    /** The intervals that have a matching replay/forward icon. */
    val ALLOWED_SECONDS = listOf(5, 10, 30)

    val Default = SeekTime(rewind = 10, forward = 30)

    fun snap(seconds: Int): Int = ALLOWED_SECONDS.minBy { abs(it - seconds) }
  }
}
