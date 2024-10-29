package org.grakovne.lissen.playback.service

import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var mediaSession: MediaSession

    @Inject
    lateinit var mediaChannel: LissenMediaProvider

    @Inject
    lateinit var playbackSynchronizationService: PlaybackSynchronizationService

    @Inject
    lateinit var sharedPreferences: LissenSharedPreferences

    @Inject
    lateinit var channelProvider: LissenMediaProvider

    private val playerServiceScope = MainScope()

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PLAY -> {
                playerServiceScope
                    .launch {
                        exoPlayer.prepare()
                        exoPlayer.setPlaybackSpeed(sharedPreferences.getPlaybackSpeed())
                        exoPlayer.playWhenReady = true
                    }
                return START_STICKY
            }

            ACTION_PAUSE -> {
                playerServiceScope
                    .launch {
                        exoPlayer.playWhenReady = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                return START_NOT_STICKY
            }

            ACTION_SET_PLAYBACK -> {
                val book = intent.getSerializableExtra(BOOK_EXTRA) as? DetailedBook
                book?.let {
                    playerServiceScope
                        .launch { preparePlayback(it) }
                }
                return START_NOT_STICKY
            }

            ACTION_SEEK_TO -> {
                val book = intent.getSerializableExtra(BOOK_EXTRA) as? DetailedBook
                val position = intent.getDoubleExtra(POSITION, 0.0)
                book?.let { seek(it.files, position) }
                return START_NOT_STICKY
            }

            else -> {
                return START_NOT_STICKY
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        playerServiceScope.cancel()

        mediaSession.release()
        exoPlayer.release()
        exoPlayer.clearMediaItems()

        super.onDestroy()
    }

    @OptIn(UnstableApi::class)
    private suspend fun preparePlayback(book: DetailedBook) {
        exoPlayer.playWhenReady = false

        withContext(Dispatchers.IO) {
            val prepareQueue = async {
                val playingQueue = book
                    .files
                    .mapNotNull { file ->
                        mediaChannel
                            .provideFileUri(book.id, file.id)
                            .fold(
                                onSuccess = {
                                    MediaItem.Builder()
                                        .setMediaId(file.id)
                                        .setUri(it)
                                        .setTag(book)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(file.name)
                                                .setArtist(book.title)
                                                .setArtworkUri(mediaChannel.provideBookCoverUri(book.id))
                                                .build()
                                        )
                                        .build()
                                },
                                onFailure = { null }
                            )
                    }

                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItems(playingQueue)
                    setPlaybackProgress(book.files, book.progress)
                }
            }

            val prepareSession = async {
                playbackSynchronizationService.startPlaybackSynchronization(book)
            }

            awaitAll(prepareSession, prepareQueue)

            LocalBroadcastManager
                .getInstance(baseContext)
                .sendBroadcast(Intent(PLAYBACK_READY))
        }
    }

    private fun seek(
        items: List<BookFile>,
        position: Double?
    ) {
        if (items.isEmpty()) {
            Log.w(TAG, "Tried to seek position $position in the empty book. Skipping")
            return
        }

        when (position) {
            null -> exoPlayer.seekTo(0, 0)
            else -> {
                val positionMs = (position * 1000).toLong()

                val durationsMs = items.map { (it.duration * 1000).toLong() }
                val cumulativeDurationsMs = durationsMs.runningFold(0L) { acc, duration -> acc + duration }

                val targetChapterIndex = cumulativeDurationsMs.indexOfFirst { it > positionMs }

                if (targetChapterIndex == -1) {
                    val lastChapterIndex = items.size - 1
                    val lastChapterDurationMs = durationsMs.last()
                    exoPlayer.seekTo(lastChapterIndex, lastChapterDurationMs)
                    return
                }

                val chapterStartTimeMs = cumulativeDurationsMs[targetChapterIndex - 1]
                val chapterProgressMs = positionMs - chapterStartTimeMs
                exoPlayer.seekTo(targetChapterIndex - 1, chapterProgressMs)
            }
        }
    }

    private fun setPlaybackProgress(
        chapters: List<BookFile>,
        progress: MediaProgress?
    ) = seek(chapters, progress?.currentTime)

    companion object {

        const val ACTION_PLAY = "org.grakovne.lissen.player.service.PLAY"
        const val ACTION_PAUSE = "org.grakovne.lissen.player.service.PAUSE"
        const val ACTION_SET_PLAYBACK = "org.grakovne.lissen.player.service.SET_PLAYBACK"
        const val ACTION_SEEK_TO = "org.grakovne.lissen.player.service.ACTION_SEEK_TO"

        const val BOOK_EXTRA = "org.grakovne.lissen.player.service.BOOK"
        const val PLAYBACK_READY = "org.grakovne.lissen.player.service.PLAYBACK_READY"
        const val POSITION = "org.grakovne.lissen.player.service.POSITION"

        private const val TAG: String = "PlaybackService"
    }
}
