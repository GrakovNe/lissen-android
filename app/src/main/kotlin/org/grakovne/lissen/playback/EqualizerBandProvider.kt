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

    private suspend fun probeCapabilities(): EqualizerCapabilities =
      withContext(Dispatchers.IO) {
        var equalizer: Equalizer? = null

        try {
          val audioManager = requireNotNull(context.getSystemService(AudioManager::class.java))
          val sessionId = audioManager.generateAudioSessionId()
          check(sessionId != AudioManager.ERROR)

          equalizer = Equalizer(0, sessionId)
          val range = equalizer.bandLevelRange

          EqualizerCapabilities(
            bands =
              (0 until equalizer.numberOfBands.toInt())
                .map { band -> BandInfo(centerFreqHz = equalizer.getCenterFreq(band.toShort()) / 1000) },
            minDb = range[0] / 100,
            maxDb = range[1] / 100,
          )
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer capabilities due to ${ex.message}")
          EqualizerCapabilities.Unavailable
        } finally {
          runCatching { equalizer?.release() }
        }
      }
  }
