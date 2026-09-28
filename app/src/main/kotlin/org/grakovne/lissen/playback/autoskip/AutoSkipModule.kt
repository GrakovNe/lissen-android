package org.grakovne.lissen.playback.autoskip

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface AutoSkipModule {
  @Binds
  @IntoSet
  fun bindAutoSkipService(service: AutoSkipService): RunningComponent
}
