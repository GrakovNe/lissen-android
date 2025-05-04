package org.grakovne.lissen.playback.service

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface PlaybackNotificationModule {
  @Binds
  @IntoSet
  fun bindPlaybackNotificationService(service: PlaybackNotificationService): RunningComponent
}
