package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.grakovne.lissen.common.AudioFocusLossPolicy
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.domain.EqualizerSettings
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

    private var equalizer: AttachedEqualizer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
      player.addListener(
        object : Player.Listener {
          override fun onAudioSessionIdChanged(id: Int) {
            attachEnhancer(id, sharedPreferences.getPlaybackVolumeBoost())
            attachEqualizer(id)
          }
        },
      )
      attachEnhancer(player.audioSessionId, sharedPreferences.getPlaybackVolumeBoost())
      attachEqualizer(player.audioSessionId)

      scope.launch {
        sharedPreferences.playbackVolumeBoostFlow.collectLatest {
          withContext(Dispatchers.Main) { updateGain(it) }
        }
      }

      scope.launch {
        sharedPreferences.equalizerFlow.collectLatest {
          withContext(Dispatchers.Main) { applyEqualizer(it) }
        }
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
    private fun attachEqualizer(sessionId: Int) {
      equalizer?.effect?.release()
      equalizer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

      scope.launch {
        val attached = buildEqualizer(sessionId) ?: return@launch

        withContext(Dispatchers.Main) {
          if (player.audioSessionId != sessionId) {
            attached.effect.release()
            return@withContext
          }

          equalizer?.effect?.release()
          equalizer = attached
          applyEqualizer(sharedPreferences.getEqualizer())
          Timber.d("Equalizer attached to audio session $sessionId with ${attached.capabilities.bands.size} bands")
        }
      }
    }

    private suspend fun buildEqualizer(sessionId: Int): AttachedEqualizer? {
      val capabilities = equalizerBandProvider.getCapabilities()

      if (!capabilities.available) {
        Timber.w("Equalizer is unavailable on this device, audio session $sessionId plays unshaped")
        return null
      }

      return withContext(Dispatchers.IO) {
        try {
          AttachedEqualizer(equalizerProcessing(sessionId, capabilities), capabilities)
        } catch (ex: Exception) {
          Timber.e("Unable to attach equalizer due to ${ex.message}")
          null
        }
      }
    }

    private fun applyEqualizer(settings: EqualizerSettings) {
      val attached = equalizer ?: return

      try {
        if (!attached.effect.hasControl()) {
          Timber.w("Equalizer lost control of the audio session, settings may not apply")
        }

        if (!settings.isActive) {
          attached.effect.enabled = false
          return
        }

        attached.effect.setPreEqAllChannelsTo(equalizerProcessingEq(attached.capabilities, settings.gains))
        attached.effect.enabled = true
      } catch (ex: Exception) {
        Timber.e("Unable to apply equalizer due to: $ex")
      }
    }

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

    private class AttachedEqualizer(
      val effect: DynamicsProcessing,
      val capabilities: EqualizerCapabilities,
    )
  }
