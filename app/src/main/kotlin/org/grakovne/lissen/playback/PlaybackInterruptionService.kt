package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.annotation.VisibleForTesting
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.grakovne.lissen.common.NetworkService
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Warns when playback stops because the server cannot be reached, and with the auto-resume
 * setting on, continues once the server answers again. It probes the server whenever the device
 * has a network, so a server that only one network can reach (a home Wi-Fi, a VPN) counts as
 * back once that network is joined.
 *
 * Any sign that the listener moved on ends the wait: a pause (also from unplugged headphones),
 * play pressed by hand, another book. The wait also gives up after [MAX_WAIT_MS], so a forgotten
 * book does not start talking hours later.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlaybackInterruptionService
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val preferences: PlaybackPreferences,
    private val networkService: NetworkService,
    private val reachability: ServerReachability,
    private val notifier: PlaybackInterruptionNotifier,
  ) : RunningComponent {
    @VisibleForTesting
    internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    @VisibleForTesting
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val scope by lazy { CoroutineScope(SupervisorJob() + mainDispatcher) }

    private var waiting: Job? = null
    private var interrupted = false

    private val listener =
      object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) = onError(error)

        override fun onPlayerErrorChanged(error: PlaybackException?) {
          // prepare() clears the error: play pressed, another book, or this service resuming
          if (error == null) settle()
        }

        override fun onPlayWhenReadyChanged(
          playWhenReady: Boolean,
          reason: Int,
        ) {
          if (playWhenReady.not() && waiting != null) {
            Timber.d("Playback paused while waiting for the server; not resuming on reconnect")
            stopWaiting()
            notifier.showServerUnreachable(autoResume = false)
          }
        }
      }

    override fun onCreate() {
      player.addListener(listener)
    }

    private fun onError(error: PlaybackException) {
      if (error.isServerUnreachable().not()) return
      // only an interrupted listening is worth a warning; a failed preparation has its own
      if (player.playWhenReady.not()) return

      interrupted = true
      val autoResume = preferences.getAutoResumeOnReconnect()
      Timber.w("Playback stopped, server unreachable (${error.errorCodeName}); autoResume=$autoResume")

      notifier.showServerUnreachable(autoResume)
      if (autoResume) startWaiting()
    }

    private fun startWaiting() {
      stopWaiting()

      waiting =
        scope.launch {
          val resumed =
            withTimeoutOrNull(MAX_WAIT_MS) {
              // a new network restarts the probing at once
              networkService.networkAvailable.collectLatest { available ->
                if (available) probeUntilReachable()
              }
            }

          if (resumed == null) {
            Timber.d("Server still unreachable after ${MAX_WAIT_MS / 60_000} min; giving up on auto-resume")
            waiting = null
            notifier.showServerUnreachable(autoResume = false)
          }
        }
    }

    private suspend fun probeUntilReachable() {
      var attempt = 0

      while (true) {
        if (probe()) {
          resume()
          return
        }

        delay(probeDelayMillis(attempt++))
      }
    }

    private suspend fun probe(): Boolean =
      try {
        withContext(ioDispatcher) { reachability.isReachable() }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        Timber.w(error, "Server reachability probe failed")
        false
      }

    private fun resume() {
      val stillInterrupted = player.playerError != null && player.playbackState == Player.STATE_IDLE

      if (stillInterrupted && player.playWhenReady) {
        Timber.d("Server reachable again; resuming playback")
        // keeps the position; settles through onPlayerErrorChanged
        player.prepare()
      }

      // ends the collection of network changes: the wait is over either way
      waiting?.cancel()
      waiting = null
    }

    private fun settle() {
      stopWaiting()

      if (interrupted) {
        interrupted = false
        notifier.cancel()
      }
    }

    private fun stopWaiting() {
      waiting?.cancel()
      waiting = null
    }

    companion object {
      const val MAX_WAIT_MS = 60 * 60_000L
    }
  }

/** 5, 10, 20, then every 30 seconds. */
internal fun probeDelayMillis(attempt: Int): Long =
  (INITIAL_PROBE_DELAY_MS shl attempt.coerceIn(0, 3)).coerceAtMost(MAX_PROBE_DELAY_MS)

private const val INITIAL_PROBE_DELAY_MS = 5_000L
private const val MAX_PROBE_DELAY_MS = 30_000L
