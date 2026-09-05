package org.grakovne.lissen.playback

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackEventBusModule {
  @Provides
  @Singleton
  fun providePlaybackEventBus(): PlaybackEventBus = PlaybackEventBus()
}
