package org.grakovne.lissen.channel.common

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.channel.audiobookshelf.AudiobookshelfChannelProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChannelModule {
  @OptIn(UnstableApi::class)
  @Provides
  @Singleton
  fun getChannelProviders(
    audiobookshelfChannelProvider: AudiobookshelfChannelProvider,
  ): Map<ChannelCode, @JvmSuppressWildcards ChannelProvider> =
    mapOf(
      audiobookshelfChannelProvider.getChannelCode() to audiobookshelfChannelProvider,
    )
}
