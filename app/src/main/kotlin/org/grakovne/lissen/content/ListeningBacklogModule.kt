package org.grakovne.lissen.content

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface ListeningBacklogModule {
  @Binds
  @IntoSet
  fun bindListeningBacklogSynchronizer(synchronizer: ListeningBacklogSynchronizer): RunningComponent
}
