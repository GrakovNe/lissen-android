package org.grakovne.lissen.content.cache.persistent

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface ContentAutoCachingModule {
  @Binds
  @IntoSet
  fun bindContentAutoCachingService(service: ContentAutoCachingService): RunningComponent
}
