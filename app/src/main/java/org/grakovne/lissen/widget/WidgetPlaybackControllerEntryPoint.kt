package org.grakovne.lissen.widget

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetPlaybackControllerEntryPoint {
  fun widgetPlaybackController(): WidgetPlaybackController
}
