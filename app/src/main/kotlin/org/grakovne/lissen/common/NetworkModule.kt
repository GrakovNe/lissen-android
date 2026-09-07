package org.grakovne.lissen.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {
  @Binds
  @IntoSet
  fun bindNetworkService(service: NetworkService): RunningComponent
}
