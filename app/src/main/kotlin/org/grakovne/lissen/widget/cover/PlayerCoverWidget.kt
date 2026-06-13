package org.grakovne.lissen.widget.cover

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.session.R.drawable.media3_icon_pause
import androidx.media3.session.R.drawable.media3_icon_play
import dagger.hilt.android.EntryPointAccessors
import org.grakovne.lissen.R.drawable
import org.grakovne.lissen.ui.theme.WidgetBackgroundDark
import org.grakovne.lissen.ui.theme.WidgetBackgroundLight
import org.grakovne.lissen.widget.WidgetPlaybackControllerEntryPoint
import org.grakovne.lissen.widget.bitmapFromFile
import org.grakovne.lissen.widget.bitmapFromResource
import org.grakovne.lissen.widget.state.PlayerStateWidget
import timber.log.Timber
import java.io.File

class PlayerCoverWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode =
    SizeMode.Responsive(
      setOf(
        DpSize(80.dp, 80.dp),
        DpSize(160.dp, 160.dp),
        DpSize(250.dp, 250.dp),
      ),
    )

  override suspend fun provideGlance(
    context: Context,
    id: GlanceId,
  ) {
    provideContent {
      val state = currentState<Preferences>()

      val maybeCoverFile = state[PlayerStateWidget.coverPath]?.takeIf { it.isNotBlank() }?.let { File(it) }

      val playingItemId = state[bookId] ?: ""
      val isPlayingNow = state[isPlaying] ?: false

      val size = LocalSize.current
      val density = context.resources.displayMetrics.density
      val targetWidthPx = (size.width.value * density).toInt().coerceIn(1, 512)
      val targetHeightPx = (size.height.value * density).toInt().coerceIn(1, 512)

      val coverBitmap =
        try {
          maybeCoverFile
            ?.takeIf { it.exists() }
            ?.let { bitmapFromFile(it.absolutePath, targetWidthPx, targetHeightPx) }
            ?: bitmapFromResource(context, drawable.cover_fallback_png, targetWidthPx, targetHeightPx)
        } catch (e: Exception) {
          bitmapFromResource(context, drawable.cover_fallback_png, targetWidthPx, targetHeightPx)
        }

      val coverImageProvider = ImageProvider(coverBitmap)

      val minSide = minOf(size.width, size.height)
      val playButtonSize = minSide * 0.5f
      val playIconSize = playButtonSize * 0.46f

      Box(
        modifier =
          GlanceModifier
            .fillMaxSize()
            .run {
              providePlayerCoverLaunchIntent(context)
                ?.let { clickable(onClick = actionStartActivity(it)) }
                ?: this
            },
        contentAlignment = Alignment.Center,
      ) {
        Image(
          provider = coverImageProvider,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = GlanceModifier.fillMaxSize(),
        )

        Box(
          modifier =
            GlanceModifier
              .size(playButtonSize)
              .cornerRadius(playButtonSize / 2)
              .background(
                day = WidgetBackgroundLight,
                night = WidgetBackgroundDark,
              ).clickable(
                onClick =
                  actionRunCallback<PlayerCoverTogglePlaybackAction>(
                    actionParametersOf(bookIdActionKey to playingItemId),
                  ),
              ),
          contentAlignment = Alignment.Center,
        ) {
          Image(
            provider =
              when (isPlayingNow) {
                true -> ImageProvider(media3_icon_pause)
                false -> ImageProvider(media3_icon_play)
              },
            contentDescription = null,
            modifier = GlanceModifier.size(playIconSize),
          )
        }
      }
    }
  }

  companion object {
    val bookIdActionKey = ActionParameters.Key<String>("player_cover_book_id")

    val coverPath = stringPreferencesKey("player_widget_key_cover")
    val bookId = stringPreferencesKey("player_widget_key_id")
    val isPlaying = booleanPreferencesKey("player_widget_key_is_playing")
  }
}

class PlayerCoverTogglePlaybackAction : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    val playingItemId = parameters[PlayerCoverWidget.bookIdActionKey] ?: return

    try {
      val playbackController =
        EntryPointAccessors
          .fromApplication(
            context = context.applicationContext,
            entryPoint = WidgetPlaybackControllerEntryPoint::class.java,
          ).widgetPlaybackController()

      when (playbackController.providePlayingItem()) {
        null -> playbackController.prepareAndRun(playingItemId) { playbackController.togglePlayPause() }
        else -> playbackController.togglePlayPause()
      }
    } catch (ex: Exception) {
      Timber.w(ex, "Unable to toggle playback from PlayerCoverWidget for %s", playingItemId)
    }
  }
}

private fun providePlayerCoverLaunchIntent(context: Context): Intent? =
  context.packageManager
    .getLaunchIntentForPackage(context.packageName)
    ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
