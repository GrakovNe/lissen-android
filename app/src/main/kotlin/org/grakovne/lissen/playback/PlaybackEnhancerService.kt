package org.grakovne.lissen.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.PlaybackVolumeBoost
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class PlaybackEnhancerService
  @OptIn(UnstableApi::class)
  @Inject
  constructor(
    private val player: ExoPlayer,
    private val sharedPreferences: LissenSharedPreferences,
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var enhancer: LoudnessEnhancer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
      player.addListener(
        object : Player.Listener {
          override fun onAudioSessionIdChanged(id: Int) {
            attachEnhancer(id, sharedPreferences.getPlaybackVolumeBoost())
          }
        },
      )
      attachEnhancer(player.audioSessionId, sharedPreferences.getPlaybackVolumeBoost())

      scope.launch {
        sharedPreferences.playbackVolumeBoostFlow.collectLatest { updateGain(it) }
      }

      updateGain(sharedPreferences.getPlaybackVolumeBoost())
    }

    @OptIn(UnstableApi::class)
    private fun attachEnhancer(
      sessionId: Int,
      boost: PlaybackVolumeBoost,
    ) {
      enhancer?.release()
      enhancer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

      enhancer = LoudnessEnhancer(sessionId)
      updateGain(boost)
    }

    private fun updateGain(value: PlaybackVolumeBoost) {
      try {
        when (value) {
          PlaybackVolumeBoost.DISABLED -> enhancer?.enabled = false
          else -> {
            enhancer?.enabled = true
            enhancer?.setTargetGain(boostToMb(value))
          }
        }
      } catch (ex: Exception) {
        Timber.e("Unable update volume gain with $value due to: $ex")
      }
    }

    private fun boostToMb(value: PlaybackVolumeBoost): Int =
      when (value) {
        PlaybackVolumeBoost.DISABLED -> 0
        PlaybackVolumeBoost.LOW -> dbToMb(3f)
        PlaybackVolumeBoost.MEDIUM -> dbToMb(6f)
        PlaybackVolumeBoost.HIGH -> dbToMb(12f)
      }

    private fun dbToMb(db: Float): Int = (db * 100f).roundToInt()
  }
