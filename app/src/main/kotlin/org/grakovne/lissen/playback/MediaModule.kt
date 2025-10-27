package org.grakovne.lissen.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
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
  ): ExoPlayer {
    val player =
      ExoPlayer
        .Builder(context)
        .setHandleAudioBecomingNoisy(true)
        .setAudioAttributes(
          AudioAttributes
            .Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build(),
          true,
        ).build()

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
