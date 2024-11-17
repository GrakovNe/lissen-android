package org.grakovne.lissen.playback.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import org.grakovne.lissen.domain.CurrentEpisodeTimerOption
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.DurationTimerOption
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.playback.service.PlaybackService.Companion.TIMER_EXPIRED
import javax.inject.Inject

@ServiceScoped
class TimerService @Inject constructor(
    @ApplicationContext val context: Context
) {

    private var selectedOption: TimerOption? = null
    private var delayBeforePause: Double? = null

    private val handler = Handler(Looper.getMainLooper())


    fun fetchTimer() = TimerState(
        selectedOption = selectedOption,
        delayBeforePause = delayBeforePause
    )

    fun setTimer(
        option: TimerOption,
        playingBook: DetailedItem,
        overAllPosition: Double
    ) {
        selectedOption = option

        when (option) {
            is DurationTimerOption -> {
                val durationMillis = option.duration * 60 * 1000.0
                sedtTimer(durationMillis)
            }

            is CurrentEpisodeTimerOption -> {
                val chapterDuration = calculateChapterIndex(playingBook, overAllPosition)
                    .let { playingBook.chapters[it] }
                    .duration

                val chapterPosition = calculateChapterPosition(
                    book = playingBook,
                    overallPosition = overAllPosition
                )

                sedtTimer((chapterDuration - chapterPosition) * 1000)
            }
        }
    }

    fun stopTimer() = cancelTimer()

    private fun cancelTimer() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun sedtTimer(delay: Double) {
        cancelTimer()

        handler.postDelayed(
            {
                val intent = Intent(context, PlaybackService::class.java).apply {
                    action = PlaybackService.ACTION_PAUSE
                }

                context.startService(intent)

                LocalBroadcastManager
                    .getInstance(context)
                    .sendBroadcast(Intent(TIMER_EXPIRED))
            },
            delay.toLong()
        )
    }
}

data class TimerState(
    val selectedOption: TimerOption? = null,
    val delayBeforePause: Double? = null
)