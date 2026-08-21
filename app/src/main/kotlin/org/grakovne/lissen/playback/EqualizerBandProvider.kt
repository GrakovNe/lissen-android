package org.grakovne.lissen.playback

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class BandInfo(
  val centerFreqHz: Int,
)

data class EqualizerCapabilities(
  val bands: List<BandInfo>,
  val minDb: Int,
  val maxDb: Int,
) {
  val available: Boolean
    get() = bands.isNotEmpty()

  companion object {
    val Unavailable = EqualizerCapabilities(bands = emptyList(), minDb = 0, maxDb = 0)
  }
}

@Singleton
class EqualizerBandProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    private val mutex = Mutex()
    private var cached: EqualizerCapabilities? = null

    suspend fun getCapabilities(): EqualizerCapabilities =
      mutex.withLock {
        cached ?: probeCapabilities().also { cached = it }
      }

    /**
     * The equalizer uses a fixed band set, so the only device-dependent fact is whether
     * DynamicsProcessing can be constructed at all. Probe it once on a throwaway audio session:
     * devices where this fails are the same ones where boost falls back to LoudnessEnhancer.
     */
    private suspend fun probeCapabilities(): EqualizerCapabilities =
      withContext(Dispatchers.IO) {
        var processor: DynamicsProcessing? = null

        try {
          val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
          val sessionId = audioManager.generateAudioSessionId()
          check(sessionId != AudioManager.ERROR)

          processor = DynamicsProcessing(0, sessionId, buildProbeConfig())
          FIXED_CAPABILITIES
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer capabilities due to ${ex.message}")
          EqualizerCapabilities.Unavailable
        } finally {
          runCatching { processor?.release() }
        }
      }

    private fun buildProbeConfig(): DynamicsProcessing.Config =
      DynamicsProcessing.Config
        .Builder(
          DynamicsProcessingTuning.VARIANT,
          DynamicsProcessingTuning.CHANNEL_COUNT,
          true, // preEqInUse
          DynamicsProcessingTuning.PRE_EQ_BAND_COUNT,
          false, // mbcInUse
          0, // mbcBandCount
          false, // postEqInUse
          0, // postEqBandCount
          false, // limiterInUse
        ).build()

    private companion object {
      val FIXED_CAPABILITIES =
        EqualizerCapabilities(
          bands = DynamicsProcessingTuning.PRE_EQ_BAND_CENTER_FREQUENCIES_HZ.map { BandInfo(centerFreqHz = it) },
          minDb = DynamicsProcessingTuning.PRE_EQ_MIN_GAIN_DB,
          maxDb = DynamicsProcessingTuning.PRE_EQ_MAX_GAIN_DB,
        )
    }
  }
