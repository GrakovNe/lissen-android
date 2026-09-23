package org.grakovne.lissen.playback

import androidx.media3.common.util.UnstableApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
interface PlayerConnectionModule {
  @Binds
  fun bindPlayerConnection(connection: MediaSessionConnection): PlayerConnection

  @Binds
  fun bindMainThread(mainThread: HandlerMainThread): MainThread
}
