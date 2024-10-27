package org.grakovne.lissen.playback

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.domain.DetailedBook
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.playback.service.PlaybackService.Companion.ACTION_SEEK_TO
import org.grakovne.lissen.playback.service.PlaybackService.Companion.BOOK_EXTRA
import org.grakovne.lissen.playback.service.PlaybackService.Companion.PLAYBACK_READY
import org.grakovne.lissen.playback.service.PlaybackService.Companion.POSITION
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences
) {

    private lateinit var mediaController: MediaController

    private val token =
        SessionToken(context, ComponentName(context, PlaybackService::class.java))

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isPlaybackReady = MutableLiveData(false)
    val isPlaybackReady: LiveData<Boolean> = _isPlaybackReady

    private val _mediaItemPosition = MutableLiveData<Double>()
    val mediaItemPosition: LiveData<Double> = _mediaItemPosition

    private val _playingBook = MutableLiveData<DetailedBook>()
    val playingBook: LiveData<DetailedBook> = _playingBook

    private val _playbackSpeed = MutableLiveData(preferences.getPlaybackSpeed())
    val playbackSpeed: LiveData<Float> = _playbackSpeed

    private val handler = Handler(Looper.getMainLooper())

    private val bookDetailsReadyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PLAYBACK_READY) {
                _isPlaybackReady.postValue(true)
            }
        }
    }

    init {
        val controllerBuilder = MediaController.Builder(context, token)
        val futureController = controllerBuilder.buildAsync()

        Futures.addCallback(
            futureController,
            object : FutureCallback<MediaController> {
                override fun onSuccess(controller: MediaController) {
                    mediaController = controller

                    LocalBroadcastManager
                        .getInstance(context)
                        .registerReceiver(bookDetailsReadyReceiver, IntentFilter(PLAYBACK_READY))

                    mediaController.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                mediaController.seekTo(0, 0)
                                mediaController.pause()
                            }
                        }
                    })
                }

                override fun onFailure(t: Throwable) {
                    throw RuntimeException("Unable to add callback to player")
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun mediaPreparing() {
        _isPlaybackReady.postValue(false)
    }

    fun preparePlayingBook(book: DetailedBook) {
        if (::mediaController.isInitialized && _playingBook.value != book) {
            preparePlay(book)
        }
        _playingBook.postValue(book)
        updateProgress(book)

        startUpdatingProgress(book)
    }

    fun play() {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pauseAudio() {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun seekTo(position: Double) {
        val intent = Intent(context, PlaybackService::class.java).apply {
            action = ACTION_SEEK_TO

            putExtra(BOOK_EXTRA, playingBook.value)
            putExtra(POSITION, position)
        }

        context.startService(intent)
    }

    fun setPlaybackSpeed(factor: Float) {
        val speed = when {
            factor < 0.5f -> 0.5f
            factor > 3f -> 3f
            else -> factor
        }

        mediaController.setPlaybackSpeed(speed)
        _playbackSpeed.postValue(speed)

        preferences.savePlaybackSpeed(speed)
    }

    private fun startUpdatingProgress(detailedBook: DetailedBook) {
        handler.removeCallbacksAndMessages(null)

        handler.postDelayed(
            object : Runnable {
                override fun run() {
                    updateProgress(detailedBook)
                    handler.postDelayed(this, 500)
                }
            },
            500
        )
    }

    private fun updateProgress(detailedBook: DetailedBook) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentIndex = mediaController.currentMediaItemIndex
            val accumulated = detailedBook.files.take(currentIndex).sumOf { it.duration }
            val currentFilePosition = mediaController.currentPosition / 1000.0

            _mediaItemPosition.value = (accumulated + currentFilePosition)
        }
    }

    private fun preparePlay(book: DetailedBook) {
        _mediaItemPosition.postValue(0.0)
        _isPlaying.postValue(false)

        val intent = Intent(context, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_SET_PLAYBACK
            putExtra(BOOK_EXTRA, book)
        }

        context.startService(intent)
    }
}
