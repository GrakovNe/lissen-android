package org.grakovne.lissen.widget

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber

suspend fun safelyRun(
  playingItemId: String,
  context: Context,
  action: (WidgetPlaybackController) -> Unit,
) = safelyUse(context, "$action on $playingItemId") { controller ->
  controller.runForItem(playingItemId) { action(controller) }
}

suspend fun safelyTogglePlayback(
  playingItemId: String,
  context: Context,
) = safelyUse(context, "play/pause toggle on $playingItemId") { it.togglePlayback(playingItemId) }

private suspend fun safelyUse(
  context: Context,
  description: String,
  block: suspend (WidgetPlaybackController) -> Unit,
) {
  try {
    val playbackController =
      EntryPointAccessors
        .fromApplication(
          context = context.applicationContext,
          entryPoint = WidgetPlaybackControllerEntryPoint::class.java,
        ).widgetPlaybackController()

    block(playbackController)
  } catch (ex: Exception) {
    Timber.w("Unable to run $description due to $ex")
  }
}
