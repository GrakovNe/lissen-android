package org.grakovne.lissen.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
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

@Singleton
class EqualizerBandProvider
  @Inject
  constructor() {
    private val mutex = Mutex()
    private var cached: List<BandInfo>? = null

    suspend fun getBands(): List<BandInfo> =
      mutex.withLock {
        cached ?: probeBands().also { cached = it }
      }

    private suspend fun probeBands(): List<BandInfo> =
      withContext(Dispatchers.IO) {
        var track: AudioTrack? = null
        var equalizer: Equalizer? = null

        try {
          track = buildProbeTrack()
          equalizer = Equalizer(0, track.audioSessionId)

          (0 until equalizer.numberOfBands.toInt())
            .map { band -> BandInfo(centerFreqHz = equalizer.getCenterFreq(band.toShort()) / 1000) }
        } catch (ex: Exception) {
          Timber.e("Unable to probe equalizer bands due to ${ex.message}")
          emptyList()
        } finally {
          runCatching { equalizer?.release() }
          runCatching { track?.release() }
        }
      }

    private fun buildProbeTrack(): AudioTrack {
      val sampleRate = 44100
      val bufferSize =
        AudioTrack
          .getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
          .coerceAtLeast(1024)

      return AudioTrack
        .Builder()
        .setAudioAttributes(
          AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build(),
        ).setAudioFormat(
          AudioFormat
            .Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        ).setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    }
  }
