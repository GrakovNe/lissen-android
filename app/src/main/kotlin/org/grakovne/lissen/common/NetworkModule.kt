package org.grakovne.lissen.common

import com.squareup.moshi.Moshi
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface NetworkModule {
  @Binds
  @IntoSet
  fun bindNetworkService(service: NetworkService): RunningComponent

  companion object {
    @Provides
    @Singleton
    fun provideMoshi(): Moshi = moshi
  }
}
