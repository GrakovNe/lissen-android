package org.grakovne.lissen.widget.cover

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import org.grakovne.lissen.R
import org.grakovne.lissen.widget.WidgetPlaybackControllerEntryPoint
import timber.log.Timber
import java.io.File

class PlayerCoverWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode = SizeMode.Single

  override suspend fun provideGlance(
    context: Context,
    id: GlanceId,
  ) {
    provideContent {
      val state = currentState<Preferences>()

      val coverFile =
        state[coverPath]
          ?.takeIf { it.isNotBlank() }
          ?.let(::File)
          ?.takeIf { it.exists() }

      val playingItemId = state[bookId] ?: ""
      val bookTitle = state[title] ?: ""
      val isPlayingNow = state[isPlaying] ?: false

      val original =
        coverFile
          ?.let { BitmapFactory.decodeFile(it.absolutePath) }
          ?: BitmapFactory.decodeResource(context.resources, R.drawable.cover_fallback_png)

      val widgetBitmap =
        try {
          original.toSafeWidgetBitmap()
        } catch (ex: Exception) {
          Timber.w(ex, "Unable to prepare widget bitmap")
          BitmapFactory
            .decodeResource(context.resources, R.drawable.cover_fallback_png)
            .toSafeWidgetBitmap()
        }

      val launchIntent = providePlayerCoverLaunchIntent(context)

      Box(
        modifier =
          GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .run {
              if (launchIntent != null) {
                clickable(onClick = actionStartActivity(launchIntent))
              } else {
                this
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        Image(
          provider = ImageProvider(widgetBitmap),
          contentDescription = if (bookTitle.isBlank()) null else bookTitle,
          contentScale = ContentScale.Crop,
          modifier = GlanceModifier.fillMaxSize(),
        )

        Column(
          modifier = GlanceModifier.fillMaxSize(),
          verticalAlignment = Alignment.Vertical.CenterVertically,
          horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
          Box(
            modifier =
              GlanceModifier
                .size(88.dp)
                .cornerRadius(44.dp)
                .clickable(
                  onClick =
                    actionRunCallback<PlayerCoverTogglePlaybackAction>(
                      actionParametersOf(bookIdActionKey to playingItemId),
                    ),
                ),
            contentAlignment = Alignment.Center,
          ) {
            Image(
              provider =
                if (isPlayingNow) {
                  ImageProvider(androidx.media3.session.R.drawable.media3_icon_pause)
                } else {
                  ImageProvider(androidx.media3.session.R.drawable.media3_icon_play)
                },
              contentDescription = if (isPlayingNow) "Pause" else "Play",
              modifier = GlanceModifier.size(36.dp),
            )
          }
        }

        Column(
          modifier = GlanceModifier.fillMaxSize(),
          verticalAlignment = Alignment.Vertical.Bottom,
          horizontalAlignment = Alignment.Horizontal.Start,
        ) {
          if (bookTitle.isNotBlank()) {
            Box(
              modifier =
                GlanceModifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
              Text(
                text = bookTitle,
                maxLines = 2,
                style = TextStyle(fontSize = 14.sp),
              )
            }
          }
        }
      }
    }
  }

  companion object {
    val bookIdActionKey = ActionParameters.Key<String>("player_cover_book_id")

    val coverPath = stringPreferencesKey("player_widget_key_cover")
    val bookId = stringPreferencesKey("player_widget_key_id")
    val title = stringPreferencesKey("player_widget_key_title")
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

      if (playbackController.providePlayingItem() == null) {
        playbackController.prepareAndRun(playingItemId) {
          playbackController.togglePlayPause()
        }
      } else {
        playbackController.togglePlayPause()
      }
    } catch (ex: Exception) {
      Timber.w(ex, "Unable to toggle playback from PlayerCoverWidget for %s", playingItemId)
    }
  }
}

private fun providePlayerCoverLaunchIntent(context: Context): Intent? =
  context.packageManager
    .getLaunchIntentForPackage(context.packageName)
    ?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

private fun Bitmap.toSafeWidgetBitmap(): Bitmap {
  val safeBitmap = createBitmap(width, height, Bitmap.Config.RGB_565)
  val canvas = Canvas(safeBitmap)
  canvas.drawBitmap(this, 0f, 0f, null)
  return safeBitmap
}
