package org.grakovne.lissen.playback.service

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SilenceMediaSource
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
import org.grakovne.lissen.LissenApplication
import org.grakovne.lissen.channel.audiobookshelf.common.api.RequestHeadersProvider
import org.grakovne.lissen.common.createOkHttpClient
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.BookFile
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import java.io.File
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

    @Inject
    lateinit var requestHeadersProvider: RequestHeadersProvider

    private val playerServiceScope = MainScope()

    private val handler = Handler(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_SET_TIMER -> {
                val delay = intent.getDoubleExtra(TIMER_VALUE_EXTRA, 0.0)

                if (delay > 0) {
                    setTimer(delay)
                }

                return START_NOT_STICKY
            }

            ACTION_CANCEL_TIMER -> {
                cancelTimer()
                return START_NOT_STICKY
            }

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
                pause()
                return START_NOT_STICKY
            }

            ACTION_SET_PLAYBACK -> {
                val book = intent.getSerializableExtra(BOOK_EXTRA) as? DetailedItem
                book?.let {
                    playerServiceScope
                        .launch { preparePlayback(it) }
                }
                return START_NOT_STICKY
            }

            ACTION_SEEK_TO -> {
                val book = intent.getSerializableExtra(BOOK_EXTRA) as? DetailedItem
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
    private suspend fun preparePlayback(book: DetailedItem) {
        exoPlayer.playWhenReady = false

        withContext(Dispatchers.IO) {
            val prepareQueue = async {
                val cover: ByteArray? = channelProvider
                    .fetchBookCover(bookId = book.id)
                    .fold(
                        onSuccess = {
                            try {
                                it.readBytes()
                            } catch (ex: Exception) {
                                null
                            }
                        },
                        onFailure = { null },
                    )

                val cachedCover = cover
                    ?.let { content ->
                        File
                            .createTempFile(book.id, null, LissenApplication.appContext.cacheDir)
                            .also { it.writeBytes(content) }
                    }

                val sourceFactory = buildDataSourceFactory()

                val playingQueue = book
                    .files
                    .map { file ->
                        mediaChannel
                            .provideFileUri(book.id, file.id)
                            .fold(
                                onSuccess = { request ->
                                    val mediaData = MediaMetadata.Builder()
                                        .setTitle(file.name)
                                        .setArtist(book.title)
                                        .setArtworkUri(cachedCover?.toUri())

                                    val mediaItem = MediaItem.Builder()
                                        .setMediaId(file.id)
                                        .setUri(request)
                                        .setTag(book)
                                        .setMediaMetadata(mediaData.build())
                                        .build()

                                    ProgressiveMediaSource
                                        .Factory(sourceFactory)
                                        .createMediaSource(mediaItem)
                                },
                                onFailure = {
                                    SilenceMediaSource((file.duration * 1000).toLong())
                                },
                            )
                    }

                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaSources(playingQueue)
                    exoPlayer.prepare()

                    setPlaybackProgress(book.files, book.progress)
                }
            }

            val prepareSession = async {
                playbackSynchronizationService.startPlaybackSynchronization(book)
            }

            awaitAll(prepareSession, prepareQueue)

            val intent = Intent(PLAYBACK_READY).apply {
                putExtra(BOOK_EXTRA, book)
            }

            LocalBroadcastManager
                .getInstance(baseContext)
                .sendBroadcast(intent)
        }
    }

    private fun setTimer(delay: Double) {
        val delayMs = delay * 1000

        cancelTimer()

        handler.postDelayed(
            {
                pause()
                LocalBroadcastManager
                    .getInstance(baseContext)
                    .sendBroadcast(Intent(TIMER_EXPIRED))
            },
            delayMs.toLong(),
        )
        Log.d(TAG, "Timer started for $delayMs ms.")
    }

    private fun cancelTimer() {
        handler.removeCallbacksAndMessages(null)
        Log.d(TAG, "Timer canceled.")
    }

    private fun pause() {
        playerServiceScope
            .launch {
                exoPlayer.playWhenReady = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
    }

    private fun seek(
        items: List<BookFile>,
        position: Double?,
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

                when (targetChapterIndex - 1 >= 0) {
                    true -> {
                        val chapterStartTimeMs = cumulativeDurationsMs[targetChapterIndex - 1]
                        val chapterProgressMs = positionMs - chapterStartTimeMs
                        exoPlayer.seekTo(targetChapterIndex - 1, chapterProgressMs)
                    }

                    false -> {
                        val lastChapterIndex = items.size - 1
                        val lastChapterDurationMs = durationsMs.last()
                        exoPlayer.seekTo(lastChapterIndex, lastChapterDurationMs)
                    }
                }
            }
        }
    }

    private fun setPlaybackProgress(
        chapters: List<BookFile>,
        progress: MediaProgress?,
    ) = seek(chapters, progress?.currentTime)

    @OptIn(UnstableApi::class)
    private fun buildDataSourceFactory(): DefaultDataSource.Factory {
        val requestHeaders = requestHeadersProvider
            .fetchRequestHeaders()
            .associate { it.name to it.value }

        val okHttpDataSourceFactory = OkHttpDataSource
            .Factory(createOkHttpClient())
            .setDefaultRequestProperties(requestHeaders)

        return DefaultDataSource.Factory(
            baseContext,
            okHttpDataSourceFactory,
        )
    }

    companion object {

        const val ACTION_PLAY = "org.grakovne.lissen.player.service.PLAY"
        const val ACTION_PAUSE = "org.grakovne.lissen.player.service.PAUSE"
        const val ACTION_SET_PLAYBACK = "org.grakovne.lissen.player.service.SET_PLAYBACK"
        const val ACTION_SEEK_TO = "org.grakovne.lissen.player.service.ACTION_SEEK_TO"
        const val ACTION_SET_TIMER = "org.grakovne.lissen.player.service.ACTION_SET_TIMER"
        const val ACTION_CANCEL_TIMER = "org.grakovne.lissen.player.service.CANCEL_TIMER"

        const val BOOK_EXTRA = "org.grakovne.lissen.player.service.BOOK"
        const val TIMER_VALUE_EXTRA = "org.grakovne.lissen.player.service.TIMER_VALUE"
        const val TIMER_EXPIRED = "org.grakovne.lissen.player.service.TIMER_EXPIRED"

        const val PLAYBACK_READY = "org.grakovne.lissen.player.service.PLAYBACK_READY"
        const val POSITION = "org.grakovne.lissen.player.service.POSITION"

        private const val TAG: String = "PlaybackService"
    }
}
