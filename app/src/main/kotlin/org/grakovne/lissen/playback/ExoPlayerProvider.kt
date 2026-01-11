package org.grakovne.lissen.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import dagger.hilt.android.qualifiers.ApplicationContext
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class ExoPlayerProvider
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val preferences: LissenSharedPreferences,
  ) {
    private var cachedPlayer: ExoPlayer? = null

    fun provideExoPlayer(): ExoPlayer {
      val player: ExoPlayer = cachedPlayer ?: createExoPlayer()

      cachedPlayer = player
      return player
    }

    fun createExoPlayer(): ExoPlayer {
      val renderersFactory = SoftwareCodecRendersFactory(context)

      val player =
        ExoPlayer
          .Builder(context)
          .setHandleAudioBecomingNoisy(true)
          .setRenderersFactory(renderersFactory)
          .setAudioAttributes(
            AudioAttributes
              .Builder()
              .setUsage(C.USAGE_MEDIA)
              .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
              .build(),
            true,
          ).build()

      if (BuildConfig.DEBUG) {
        player.addAnalyticsListener(mediaCodecListener())
      }

      return player
    }
  }

@UnstableApi
private fun mediaCodecListener(): AnalyticsListener =
  object : AnalyticsListener {
    override fun onAudioDecoderInitialized(
      eventTime: AnalyticsListener.EventTime,
      decoderName: String,
      initializedTimestampMs: Long,
      initializationDurationMs: Long,
    ) {
      Timber.d("Audio decoder initialized: $decoderName")
    }
  }
