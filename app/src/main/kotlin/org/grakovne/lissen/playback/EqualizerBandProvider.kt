package org.grakovne.lissen.playback

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
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
  // upper edge of the band as the device reports it; the processing effect is cut at the same
  // frequencies, so the bands the listener sees are the bands that get shaped
  val upperFreqHz: Int,
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

    // the band layout and the gain range come from the platform equalizer, the audio itself is
    // shaped by DynamicsProcessing (see PlaybackEnhancerService), so both effects must exist
    private suspend fun probeCapabilities(): EqualizerCapabilities =
      withContext(Dispatchers.IO) {
        var equalizer: Equalizer? = null
        var processing: DynamicsProcessing? = null

        try {
          val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
          val sessionId = audioManager.generateAudioSessionId()
          check(sessionId != AudioManager.ERROR)

          equalizer = Equalizer(0, sessionId)
          val range = equalizer.bandLevelRange
          val bands =
            (0 until equalizer.numberOfBands.toInt())
              .map { band ->
                BandInfo(
                  centerFreqHz = equalizer.getCenterFreq(band.toShort()) / 1000,
                  upperFreqHz = equalizer.getBandFreqRange(band.toShort())[1] / 1000,
                )
              }

          processing = DynamicsProcessing(0, sessionId, equalizerProcessingConfig(bands))

          EqualizerCapabilities(
            bands = bands,
            minDb = range[0] / 100,
            maxDb = range[1] / 100,
          )
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer capabilities due to ${ex.message}")
          EqualizerCapabilities.Unavailable
        } finally {
          runCatching { processing?.release() }
          runCatching { equalizer?.release() }
        }
      }
  }
