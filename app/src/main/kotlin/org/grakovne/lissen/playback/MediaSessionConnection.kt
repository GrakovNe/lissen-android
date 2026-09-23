package org.grakovne.lissen.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.playback.service.PlaybackService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MediaSessionConnection
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
  ) : PlayerConnection {
    private val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    private val futureController: ListenableFuture<MediaController> = MediaController.Builder(context, token).buildAsync()

    @Volatile
    private var controller: MediaController? = null
    private val deferredActions = DeferredActions()

    override val isConnected: Boolean get() = controller != null
    override val isPlaying: Boolean get() = controller?.isPlaying ?: false
    override val currentMediaItemIndex: Int get() = controller?.currentMediaItemIndex ?: 0
    override val currentPositionMs: Long get() = controller?.currentPosition ?: 0L

    override fun connect(
      listener: PlayerConnection.Listener,
      onConnected: () -> Unit,
    ) {
      Futures.addCallback(
        futureController,
        object : FutureCallback<MediaController> {
          override fun onSuccess(result: MediaController) {
            controller = result
            result.addListener(listener.asPlayerListener())
            onConnected()
            deferredActions.drain()
          }

          override fun onFailure(t: Throwable) {
            Timber.e(t, "Unable to connect to the playback session")
          }
        },
        MoreExecutors.directExecutor(),
      )
    }

    override fun whenConnected(action: () -> Unit) {
      when (controller) {
        null -> {
          Timber.w("Command requested before media controller connected; deferring until connected")
          deferredActions.defer(action)
        }

        else -> {
          action()
        }
      }
    }

    override fun play(speed: Float) {
      controller?.apply {
        prepare()
        setPlaybackSpeed(speed)
        play()
      }
    }

    override fun pause() {
      controller?.pause()
    }

    override fun seekTo(
      mediaItemIndex: Int,
      positionMs: Long,
    ) {
      controller?.seekTo(mediaItemIndex, positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
      controller?.setPlaybackSpeed(speed)
    }

    override fun clear() {
      controller?.apply {
        stop()
        clearMediaItems()
      }
    }

    override fun release() {
      MediaController.releaseFuture(futureController)
    }

    private fun PlayerConnection.Listener.asPlayerListener(): Player.Listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = this@asPlayerListener.onIsPlayingChanged(isPlaying)

        override fun onPositionDiscontinuity(
          oldPosition: Player.PositionInfo,
          newPosition: Player.PositionInfo,
          reason: Int,
        ) = this@asPlayerListener.onPositionDiscontinuity()

        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) this@asPlayerListener.onEnded()
        }

        override fun onPlayerError(error: PlaybackException) = this@asPlayerListener.onError(error)
      }
  }
