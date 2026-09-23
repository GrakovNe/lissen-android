package org.grakovne.lissen.playback

import android.media.audiofx.LoudnessEnhancer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
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
    private val sharedPreferences: PlaybackPreferences,
    private val equalizerBandProvider: EqualizerBandProvider,
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
        sharedPreferences.playbackVolumeBoostFlow.collectLatest {
          withContext(Dispatchers.Main) { updateGain(it) }
        }
      }

      scope.launch {
        equalizerEffects(audioSessionIds(), equalizerBandProvider::getCapabilities, ::DynamicsProcessingEqualizer)
          .flowOn(Dispatchers.IO)
          .applying(sharedPreferences.equalizerFlow)
          .collect()
      }

      scope.launch {
        sharedPreferences.audioFocusLossPolicyFlow.collectLatest { applyAudioFocusLossPolicy(it) }
      }

      updateGain(sharedPreferences.getPlaybackVolumeBoost())
    }

    @OptIn(UnstableApi::class)
    private fun attachEnhancer(
      sessionId: Int,
      db: Int,
    ) {
      enhancer?.release()
      enhancer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

      try {
        enhancer = LoudnessEnhancer(sessionId)
        updateGain(db)
      } catch (ex: Exception) {
        Timber.e("Unable to attach LoudnessEnhancer due to ${ex.message}")
      }
    }

    private fun updateGain(db: Int) {
      try {
        if (db <= 0) {
          enhancer?.enabled = false
        } else {
          enhancer?.enabled = true
          enhancer?.setTargetGain(dbToMb(db.toFloat()))
        }
      } catch (ex: Exception) {
        Timber.e("Unable update volume gain with $db dB due to: $ex")
      }
    }

    @OptIn(UnstableApi::class)
    private fun audioSessionIds(): Flow<Int> =
      callbackFlow {
        val listener =
          object : Player.Listener {
            override fun onAudioSessionIdChanged(id: Int) {
              trySend(id)
            }
          }

        player.addListener(listener)
        trySend(player.audioSessionId)
        awaitClose { player.removeListener(listener) }
      }.flowOn(Dispatchers.Main)

    @OptIn(UnstableApi::class)
    private suspend fun applyAudioFocusLossPolicy(policy: AudioFocusLossPolicy) {
      val contentType =
        when (policy) {
          AudioFocusLossPolicy.LOWER_VOLUME -> C.AUDIO_CONTENT_TYPE_MUSIC
          AudioFocusLossPolicy.PAUSE -> C.AUDIO_CONTENT_TYPE_SPEECH
        }
      withContext(Dispatchers.Main) {
        player.setAudioAttributes(
          AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(contentType)
            .build(),
          true,
        )
      }
    }

    private fun dbToMb(db: Float): Int = (db * 100f).roundToInt()
  }
