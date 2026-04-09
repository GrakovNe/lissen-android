package org.grakovne.lissen.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.shortcuts.ContinuePlaybackShortcut

@Module
@InstallIn(SingletonComponent::class)
interface CompletePlaybackModule {
  @Binds
  @IntoSet
  fun bindCompletePlayingItemService(service: CompletePlayingItemService): RunningComponent
}
