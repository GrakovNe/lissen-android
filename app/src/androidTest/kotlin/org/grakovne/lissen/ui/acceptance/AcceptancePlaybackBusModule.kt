package org.grakovne.lissen.ui.acceptance

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import org.grakovne.lissen.playback.PlaybackEventBus
import org.grakovne.lissen.playback.PlaybackEventBusModule

/**
 * The playback service survives across instrumented tests while Hilt recreates the singleton
 * component for every test. A per-component event bus would leave the running service listening
 * to a stale bus, so instrumented tests share one process-wide bus - exactly what a single
 * application process provides in production.
 */
@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [PlaybackEventBusModule::class],
)
object AcceptancePlaybackBusModule {
  private val sharedBus = PlaybackEventBus()

  @Provides
  fun providePlaybackEventBus(): PlaybackEventBus = sharedBus
}
