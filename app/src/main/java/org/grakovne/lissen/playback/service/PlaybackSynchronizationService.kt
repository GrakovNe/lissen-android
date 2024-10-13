package org.grakovne.lissen.playback.service

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannel
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.PlaybackProgress
import org.grakovne.lissen.domain.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackSynchronizationService @Inject constructor(
    private val exoPlayer: ExoPlayer,
    private val channel: AudiobookshelfChannel
) {
    private var syncJob: Job? = null

    private var playbackSession: PlaybackSession? = null
    private val serviceScope = MainScope()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                when (isPlaying) {
                    true -> playbackSession?.let(::startPlaybackSynchronization)
                    false -> {
                        executeSynchronization()
                        stopPlaybackSynchronization()
                    }
                }
            }
        })
    }

    fun startPlaybackSynchronization(session: PlaybackSession) {
        playbackSession = session
        syncJob?.cancel()

        syncJob = serviceScope.launch {
            while (isActive) {
                executeSynchronization()
                delay(synchronizationInterval)
            }
        }
    }

    fun stopPlaybackSynchronization() {
        syncJob?.cancel()

        playbackSession = null
        syncJob = null
    }

    private fun executeSynchronization() {
        val elapsedMs = exoPlayer.currentPosition
        val overallProgress = getProgress(elapsedMs)

        serviceScope.launch(Dispatchers.IO) {
            playbackSession
                ?.let {
                    channel.syncProgress(
                        itemId = it.sessionId,
                        progress = overallProgress
                    )
                }
        }
    }

    private fun getProgress(currentElapsedMs: Long): PlaybackProgress {
        val currentBook = exoPlayer.currentMediaItem?.localConfiguration?.tag as? DetailedBook
            ?: return PlaybackProgress(0.0, 0.0)

        val currentIndex = exoPlayer.currentMediaItemIndex

        val previousDuration = currentBook.chapters
            .take(currentIndex)
            .sumOf { it.duration * 1000 }

        val totalDuration = currentBook.chapters.sumOf { it.duration * 1000 }

        val totalElapsedMs = previousDuration + currentElapsedMs
        return PlaybackProgress(
            currentTime = totalElapsedMs / 1000.0,
            totalTime = totalDuration / 1000.0
        )
    }

    companion object {
        private val synchronizationInterval = 30000L
    }

}