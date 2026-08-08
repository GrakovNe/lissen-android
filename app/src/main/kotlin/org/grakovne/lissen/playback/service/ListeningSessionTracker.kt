package org.grakovne.lissen.playback.service

import android.os.SystemClock
import org.grakovne.lissen.domain.ListeningSession
import org.grakovne.lissen.domain.PlaybackProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListeningSessionTracker
  @Inject
  constructor() {
    private var session: ListeningSession? = null
    private var playingSince: Long? = null

    fun advance(
      itemId: String,
      chapterId: String?,
      progress: PlaybackProgress,
      isPlaying: Boolean,
    ): ListeningSession {
      val now = System.currentTimeMillis()
      val mark = SystemClock.elapsedRealtime()
      val playedMs = playingSince?.let { mark - it } ?: 0

      playingSince = mark.takeIf { isPlaying }

      return chooseListeningSession(session, itemId, chapterId, progress, now)
        .let { accumulateListening(it, playedMs, progress, now) }
        .also { session = it }
    }

    fun reset() {
      session = null
      playingSince = null
    }
  }
