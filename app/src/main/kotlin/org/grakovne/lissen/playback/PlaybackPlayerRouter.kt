package org.grakovne.lissen.playback

import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.PlaybackService
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the notion of "the player that is currently producing audio".
 *
 * Normally that is the local [ExoPlayer]; while casting it is a remote player. The
 * media session and every service that listens to playback are pointed at whichever
 * player is active, and the queue, position, speed and play state are carried over on
 * each switch.
 */
@Singleton
@UnstableApi
class PlaybackPlayerRouter
  @Inject
  constructor(
    private val exoPlayer: ExoPlayer,
    private val preferences: PlaybackPreferences,
  ) {
    private val _player = MutableStateFlow<Player>(exoPlayer)
    val player: StateFlow<Player> = _player.asStateFlow()

    val current: Player
      get() = _player.value

    val isRemote: Boolean
      get() = current !== exoPlayer

    private val followers = CopyOnWriteArraySet<Player.Listener>()
    private var session: MediaSession? = null

    fun attachSession(session: MediaSession) {
      this.session = session
    }

    fun detachSession(session: MediaSession) {
      if (this.session === session) this.session = null
    }

    /** Registers a listener that keeps following the active player across switches. */
    fun addListener(listener: Player.Listener) {
      followers += listener
      current.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
      followers -= listener
      current.removeListener(listener)
    }

    @MainThread
    fun switchTo(target: Player) {
      val source = current
      if (source === target) return

      Timber.d("Switching active player from ${source::class.simpleName} to ${target::class.simpleName}")

      // The queue is rebuilt from the playing book rather than copied from the source
      // player: ExoPlayer reports the media items of its prepared sources, which for
      // clipped chapters are the internal file items without the chapter metadata.
      val queue =
        preferences
          .getPlayingItem()
          ?.takeIf { it.chapters.isNotEmpty() }
          ?.let { PlaybackService.bookToChapterMediaItems(it) }

      val sameQueue = queue != null && source.mediaItemCount == queue.mediaItems.size
      val index = if (sameQueue) source.currentMediaItemIndex else queue?.startIndex ?: 0
      val position = if (sameQueue) source.currentPosition.coerceAtLeast(0L) else queue?.startPositionMs ?: 0L
      val playWhenReady = source.playWhenReady
      val speed = source.playbackParameters.speed
      val prepared = source.playbackState != Player.STATE_IDLE && source.mediaItemCount > 0

      followers.forEach { source.removeListener(it) }
      source.stop()

      if (queue != null) {
        target.setMediaItems(queue.mediaItems, index, position)
        target.setPlaybackSpeed(speed)
        if (prepared) target.prepare()
        target.playWhenReady = playWhenReady
      } else {
        target.clearMediaItems()
      }

      followers.forEach { target.addListener(it) }
      session?.player = target
      _player.value = target
    }

    @MainThread
    fun switchToLocal() = switchTo(exoPlayer)
  }
