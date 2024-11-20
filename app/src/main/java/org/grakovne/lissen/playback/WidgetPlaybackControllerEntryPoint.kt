package org.grakovne.lissen.playback

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.grakovne.lissen.ui.widget.WidgetPlaybackController

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetPlaybackControllerEntryPoint {
    fun widgetPlaybackController(): WidgetPlaybackController
}
