package org.grakovne.lissen.shortcuts

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface ShortcutsModule {
  @Binds
  @IntoSet
  fun bindPlaybackNotificationService(service: ContinuePlaybackShortcut): RunningComponent
}
