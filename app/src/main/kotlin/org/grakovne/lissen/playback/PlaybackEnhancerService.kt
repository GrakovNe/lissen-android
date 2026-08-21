package org.grakovne.lissen.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
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
  ) : RunningComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var dynamicsProcessing: DynamicsProcessing? = null

    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var equalizer: Equalizer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
      player.addListener(
        object : Player.Listener {
          override fun onAudioSessionIdChanged(id: Int) {
            attachEnhancer(id, sharedPreferences.getPlaybackVolumeBoost())
            attachEqualizer(id, sharedPreferences.getEqualizer())
          }
        },
      )
      attachEnhancer(player.audioSessionId, sharedPreferences.getPlaybackVolumeBoost())
      attachEqualizer(player.audioSessionId, sharedPreferences.getEqualizer())

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

    // Boost goes through DynamicsProcessing (compressor + limiter) because LoudnessEnhancer
    // has no limiter we control and clips audibly from ~6 dB of boost on some devices.
    // LoudnessEnhancer stays as a best-effort fallback for sessions where the richer
    // effect cannot attach.
    @OptIn(UnstableApi::class)
    private fun attachEnhancer(
      sessionId: Int,
      db: Int,
    ) {
      dynamicsProcessing?.release()
      dynamicsProcessing = null
      loudnessEnhancer?.release()
      loudnessEnhancer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

      val effect =
        try {
          createDynamicsProcessing(sessionId, db)
        } catch (ex: Exception) {
          Timber.e("Unable to attach DynamicsProcessing due to ${ex.message}")
          null
        }

      if (effect != null) {
        dynamicsProcessing = effect
      } else {
        attachLoudnessEnhancer(sessionId)
      }

      updateGain(db)
    }

    private fun attachLoudnessEnhancer(sessionId: Int) {
      try {
        loudnessEnhancer = LoudnessEnhancer(sessionId)
      } catch (ex: Exception) {
        Timber.e("Unable to attach LoudnessEnhancer due to ${ex.message}")
      }
    }

    private fun createDynamicsProcessing(
      sessionId: Int,
      db: Int,
    ): DynamicsProcessing {
      val config =
        DynamicsProcessing.Config
          .Builder(
            DynamicsProcessingTuning.VARIANT,
            DynamicsProcessingTuning.CHANNEL_COUNT,
            false, // preEqInUse
            0, // preEqBandCount
            true, // mbcInUse
            DynamicsProcessingTuning.MBC_BAND_COUNT,
            false, // postEqInUse
            0, // postEqBandCount
            true, // limiterInUse
          ).setMbcAllChannelsTo(buildMbc(db.toFloat()))
          .setLimiterAllChannelsTo(buildLimiter())
          .build()

      // Constructor order: priority, audioSession, config.
      return DynamicsProcessing(0, sessionId, config)
    }

    private fun buildMbc(postGainDb: Float): DynamicsProcessing.Mbc {
      val band =
        DynamicsProcessing.MbcBand(
          true, // enabled
          DynamicsProcessingTuning.MBC_BAND_CUTOFF_FREQUENCY_HZ,
          DynamicsProcessingTuning.MBC_ATTACK_MS,
          DynamicsProcessingTuning.MBC_RELEASE_MS,
          DynamicsProcessingTuning.MBC_RATIO,
          DynamicsProcessingTuning.MBC_THRESHOLD_DB,
          DynamicsProcessingTuning.MBC_KNEE_WIDTH_DB,
          DynamicsProcessingTuning.MBC_NOISE_GATE_THRESHOLD_DB,
          DynamicsProcessingTuning.MBC_EXPANDER_RATIO,
          DynamicsProcessingTuning.MBC_PRE_GAIN_DB,
          postGainDb,
        )

      // Constructor order: inUse, enabled, bandCount.
      val mbc = DynamicsProcessing.Mbc(true, true, DynamicsProcessingTuning.MBC_BAND_COUNT)
      mbc.setBand(0, band)
      return mbc
    }

    private fun buildLimiter(): DynamicsProcessing.Limiter =
      // Constructor order: inUse, enabled, linkGroup, attackTime, releaseTime, ratio,
      // threshold, postGain.
      DynamicsProcessing.Limiter(
        true, // inUse
        true, // enabled
        DynamicsProcessingTuning.LIMITER_LINK_GROUP,
        DynamicsProcessingTuning.LIMITER_ATTACK_MS,
        DynamicsProcessingTuning.LIMITER_RELEASE_MS,
        DynamicsProcessingTuning.LIMITER_RATIO,
        DynamicsProcessingTuning.LIMITER_THRESHOLD_DB,
        DynamicsProcessingTuning.LIMITER_POST_GAIN_DB,
      )

    private fun updateGain(db: Int) {
      try {
        val processor = dynamicsProcessing
        val fallback = loudnessEnhancer

        if (db <= 0) {
          processor?.enabled = false
          fallback?.enabled = false
        } else if (processor != null) {
          processor.enabled = true
          processor.setMbcAllChannelsTo(buildMbc(db.toFloat()))
        } else {
          fallback?.enabled = true
          fallback?.setTargetGain(loudnessEnhancerGainMb(db))
        }
      } catch (ex: Exception) {
        Timber.e("Unable update volume gain with $db dB due to: $ex")
      }
    }

    @OptIn(UnstableApi::class)
    private fun attachEqualizer(
      sessionId: Int,
      settings: EqualizerSettings,
    ) {
      equalizer?.release()
      equalizer = null

      if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

      try {
        equalizer = Equalizer(0, sessionId)
        applyEqualizer(settings)
      } catch (ex: Exception) {
        Timber.e("Unable to attach Equalizer due to ${ex.message}")
      }
    }

    private fun applyEqualizer(settings: EqualizerSettings) {
      try {
        val eq = equalizer ?: return

        if (!eq.hasControl()) {
          Timber.w("Equalizer lost control of the audio session, settings may not apply")
        }

        if (!settings.isActive) {
          eq.enabled = false
          return
        }

        eq.enabled = true
        val range = eq.bandLevelRange

        for (band in 0 until eq.numberOfBands.toInt()) {
          eq.setBandLevel(band.toShort(), equalizerBandLevel(settings.gains, band, range[0], range[1]))
        }
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

    /** Maps a boost in dB to the millibels expected by LoudnessEnhancer.setTargetGain. */
    private fun loudnessEnhancerGainMb(boostDb: Int): Int = (boostDb * 100f).roundToInt()
  }
