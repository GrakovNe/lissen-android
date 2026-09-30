package org.grakovne.lissen.widget

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

suspend fun safelyRun(
  playingItemId: String,
  context: Context,
  action: (WidgetPlaybackController) -> Unit,
) {
  try {
    val playbackController =
      EntryPointAccessors
        .fromApplication(
          context = context.applicationContext,
          entryPoint = WidgetPlaybackControllerEntryPoint::class.java,
        ).widgetPlaybackController()

    playbackController.runForItem(playingItemId) { action(playbackController) }
  } catch (ex: Exception) {
    Timber.w("Unable to run $action on $playingItemId due to $ex")
  }
}
