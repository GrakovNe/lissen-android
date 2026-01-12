package org.grakovne.lissen.playback

import android.content.Context
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.BuildConfig
import org.grakovne.lissen.R
import org.grakovne.lissen.persistence.preferences.LissenSharedPreferences
import timber.log.Timber
import java.io.File
import java.lang.Exception
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {
  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideMediaCache(
    @ApplicationContext context: Context,
  ): Cache {
    val baseFolder =
      context
        .externalCacheDir
        ?.takeIf { it.exists() && it.canWrite() }
        ?: context.cacheDir

    return SimpleCache(
      File(baseFolder, "playback_cache"),
      LeastRecentlyUsedCacheEvictor(buildPlaybackCacheLimit(context)),
      StandaloneDatabaseProvider(context),
    )
  }

  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun provideExoPlayer(
    @ApplicationContext context: Context,
    preferences: LissenSharedPreferences,
  ): ExoPlayer {
    val renderersFactory =
      when (preferences.getSoftwareCodecsEnabled()) {
        true -> SoftwareCodecRendersFactory(context)
        false -> DefaultRenderersFactory(context)
      }

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

    player.addAnalyticsListener(mediaCodecListener(context))

    return player
  }

  private fun buildPlaybackCacheLimit(ctx: Context): Long {
    val baseFolder =
      ctx
        .externalCacheDir
        ?.takeIf { it.exists() && it.canWrite() }
        ?: ctx.cacheDir

    val stat = android.os.StatFs(baseFolder.path)
    val available = stat.availableBytes
    val dynamicCap = (available - KEEP_FREE_BYTES).coerceAtLeast(MIN_CACHE_BYTES)

    return minOf(MAX_CACHE_BYTES, dynamicCap)
  }

  private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
  private const val KEEP_FREE_BYTES = 20L * 1024 * 1024
  private const val MIN_CACHE_BYTES = 10L * 1024 * 1024
}

@UnstableApi
private fun mediaCodecListener(context: Context): AnalyticsListener =
  object : AnalyticsListener {
    override fun onAudioDecoderInitialized(
      eventTime: AnalyticsListener.EventTime,
      decoderName: String,
      initializedTimestampMs: Long,
      initializationDurationMs: Long,
    ) {
      Timber.d("Audio decoder initialized: $decoderName")
    }

    override fun onAudioCodecError(
      eventTime: AnalyticsListener.EventTime,
      audioCodecError: Exception,
    ) {
      Toast
        .makeText(
          context,
          context.getString(R.string.codes_not_supported_warning_toast),
          LENGTH_SHORT,
        ).show()

      super.onAudioCodecError(eventTime, audioCodecError)
    }
  }
