package org.grakovne.lissen.playback

import android.content.Context
import android.media.AudioManager
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
  // the processing effect is cut at the same edges, so the bands the listener sees are the bands
  // that get shaped
  val upperFreqHz: Int,
)

sealed interface EqualizerCapabilities {
  data object Unavailable : EqualizerCapabilities

  data class Available(
    val bands: List<BandInfo>,
    val minDb: Int,
    val maxDb: Int,
  ) : EqualizerCapabilities
}

@Singleton
class EqualizerBandProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
  ) {
    private val mutex = Mutex()
    private var cached: EqualizerCapabilities.Available? = null

    // a failed probe is not cached: AudioFlinger may refuse an effect momentarily, and the
    // equalizer should come back on the next attempt rather than stay hidden for the process
    suspend fun getCapabilities(): EqualizerCapabilities =
      mutex.withLock {
        cached ?: probeCapabilities().also { cached = it as? EqualizerCapabilities.Available }
      }

    // the band layout and the gain range come from the platform equalizer, the audio itself is
    // shaped by DynamicsProcessing, so both effects must exist on this device
    private suspend fun probeCapabilities(): EqualizerCapabilities =
      withContext(Dispatchers.IO) {
        try {
          val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
          val sessionId = audioManager.generateAudioSessionId()
          check(sessionId != AudioManager.ERROR)

          Equalizer(0, sessionId).use { equalizer ->
            equalizer.capabilities().also { DynamicsProcessingEqualizer(sessionId, it).close() }
          }
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer capabilities due to ${ex.message}")
          EqualizerCapabilities.Unavailable
        }
      }

    private fun Equalizer.capabilities(): EqualizerCapabilities.Available =
      EqualizerCapabilities.Available(
        bands =
          (0 until numberOfBands.toInt())
            .map { band ->
              BandInfo(
                centerFreqHz = getCenterFreq(band.toShort()) / 1000,
                upperFreqHz = getBandFreqRange(band.toShort())[1] / 1000,
              )
            },
        minDb = bandLevelRange[0] / 100,
        maxDb = bandLevelRange[1] / 100,
      )

    private inline fun <R> Equalizer.use(block: (Equalizer) -> R): R =
      try {
        block(this)
      } finally {
        runCatching { release() }
      }
  }
