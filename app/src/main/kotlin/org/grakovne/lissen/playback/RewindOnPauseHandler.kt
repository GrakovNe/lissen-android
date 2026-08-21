package org.grakovne.lissen.playback

import androidx.media3.common.Player
import org.grakovne.lissen.domain.RewindOnPauseTime
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.playback.service.RewindTarget
import org.grakovne.lissen.playback.service.calculateRewindSeconds
import org.grakovne.lissen.playback.service.calculateRewindTarget
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewindOnPauseHandler
  @Inject
  constructor(
    private val preferences: PlaybackPreferences,
  ) : Player.Listener {
    private var attachedPlayer: Player? = null

    // Id of the book currently loaded in the player. Unlike the preferences'
    // playing item it is only refreshed when a book starts playing, so it
    // still holds the stopping book when the stop event of a switch fires
    // (the preferences already point at the next book by then).
    private var activeBookId: String? = null

    // True when the last stop came from a playback suppression reason. The
    // stop is armed for suppression only because a buffering stall also makes
    // isPlaying false and must not rewind: on the automatic resume after a
    // transient audio focus loss the player restarts by itself (no play() or
    // setPlayWhenReady(true) reaches the forwarding player), so this flag is
    // the only way to apply the rewind for that path.
    private var suppressionResumePending = false

    fun attach(player: Player) {
      if (attachedPlayer === player) return
      attachedPlayer = player
      player.addListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (!isPlaying) {
        // Media3 updates the playback info before dispatching listener
        // callbacks, so the getter already holds the value for this stop.
        val player = attachedPlayer
        suppressionResumePending =
          player != null &&
          player.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE
      } else {
        // Fallback for resume paths that do not go through applyRewind: by
        // the time playback starts, the preferences already hold the book
        // that is playing.
        preferences.getPlayingItem()?.id?.let { activeBookId = it }

        if (suppressionResumePending) {
          attachedPlayer?.let { applyRewind(it) }
          suppressionResumePending = false
        }
      }

      activeBookId?.let { preferences.markBookLastActive(it, System.currentTimeMillis()) }
    }

    fun applyRewind(player: Player) {
      // A user play supersedes a pending automatic resume: the play() path
      // applies the rewind now, so the resume that follows must not apply it
      // a second time.
      suppressionResumePending = false

      // applyRewind runs from play(), after startPreparingPlayback stored the
      // book that is about to play, so the playing item is the right source
      // for the book id.
      val playingBook = preferences.getPlayingItem()
      activeBookId = playingBook?.id

      val setting = preferences.getRewindOnPauseTime()
      if (!setting.enabled || playingBook == null) return

      val target =
        decideRewindTarget(
          setting = setting,
          storedLastActiveMillis = preferences.getBookLastActive(playingBook.id),
          nowMillis = System.currentTimeMillis(),
          chapterIndex = player.currentMediaItemIndex,
          positionMillis = player.currentPosition,
        ) ?: return

      Timber.d("Rewind on pause: seeking to chapter=${target.chapterIndex} position=${target.positionMillis}ms")
      player.seekTo(target.chapterIndex, target.positionMillis)
    }
  }

internal fun decideRewindTarget(
  setting: RewindOnPauseTime,
  storedLastActiveMillis: Long?,
  nowMillis: Long,
  chapterIndex: Int,
  positionMillis: Long,
): RewindTarget? {
  if (!setting.enabled) return null
  if (chapterIndex < 0 || positionMillis < 0) return null

  // Without a stored timestamp the pause length is unknown, so treat it as the
  // full rewind window or longer and apply the full rewind.
  val pausedMillis = storedLastActiveMillis?.let { nowMillis - it } ?: Long.MAX_VALUE

  val rewindSeconds = calculateRewindSeconds(setting.seconds, pausedMillis)
  val current = RewindTarget(chapterIndex, positionMillis)
  val target = calculateRewindTarget(chapterIndex, positionMillis, rewindSeconds)

  return if (target == current) null else target
}
