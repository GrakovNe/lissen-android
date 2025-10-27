package org.grakovne.lissen.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.decodeResource
import android.graphics.Canvas
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily.Companion.SansSerif
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.R
import dagger.hilt.android.EntryPointAccessors
import org.grakovne.lissen.R.drawable
import org.grakovne.lissen.ui.theme.LightBackground
import org.grakovne.lissen.widget.PlayerWidget.Companion.bookIdKey
import timber.log.Timber
import java.io.File

class PlayerWidget : GlanceAppWidget() {
  override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

  override suspend fun provideGlance(
    context: Context,
    id: GlanceId,
  ) {
    provideContent {
      GlanceTheme(
        colors =
          ColorProviders(
            light =
              lightColorScheme(
                background = LightBackground,
              ),
            dark = darkColorScheme(),
          ),
      ) {
        val preferences =
          EntryPointAccessors
            .fromApplication(context, WidgetPreferencesEntryPoint::class.java)
            .lissenSharedPreferences()

        val state = currentState<Preferences>()
        val maybeCoverFile = state[coverPath]?.takeIf { it.isNotBlank() }?.let { File(it) }
        val bookId = state[bookId] ?: ""
        val bookTitle = state[title] ?: ""
        val chapterTitle =
          state[chapterTitle]
            ?.takeIf { it.isNotBlank() }
            ?: when (bookId) {
              "" -> context.getString(org.grakovne.lissen.R.string.widget_placeholder_text)
              else -> ""
            }

        val isPlaying = state[isPlaying] ?: false

        Column(
          modifier =
            GlanceModifier
              .fillMaxWidth()
              .background(GlanceTheme.colors.background)
              .padding(16.dp)
              .safelyRunOnClick(context),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
              GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
          ) {
            val original =
              maybeCoverFile
                ?.let { BitmapFactory.decodeFile(it.absolutePath) }
                ?: decodeResource(context.resources, drawable.cover_fallback_png)

            val coverImageProvider =
              try {
                val safeBitmap = createBitmap(original.width, original.height, Bitmap.Config.RGB_565)
                val canvas = Canvas(safeBitmap)
                canvas.drawBitmap(original, 0f, 0f, null)
                ImageProvider(safeBitmap)
              } catch (e: Exception) {
                ImageProvider(drawable.cover_fallback_png)
              }

            Image(
              contentScale = ContentScale.FillBounds,
              provider = coverImageProvider,
              contentDescription = null,
              modifier =
                GlanceModifier
                  .size(80.dp)
                  .cornerRadius(8.dp),
            )

            Column(
              modifier =
                GlanceModifier
                  .fillMaxWidth()
                  .padding(start = 20.dp),
            ) {
              Text(
                text = chapterTitle,
                style =
                  TextStyle(
                    fontFamily = SansSerif,
                    fontSize = 20.sp,
                    color = GlanceTheme.colors.onBackground,
                  ),
                maxLines = 2,
                modifier = GlanceModifier.padding(bottom = 8.dp),
              )

              Text(
                text = bookTitle,
                style =
                  TextStyle(
                    fontFamily = SansSerif,
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onBackground,
                  ),
                maxLines = 1,
              )
            }
          }

          Spacer(
            modifier =
              GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFDADADA)),
          )

          Row(
            modifier =
              GlanceModifier
                .padding(top = 16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            WidgetControlButton(
              size = 36.dp,
              icon = ImageProvider(R.drawable.media3_icon_previous),
              contentColor = GlanceTheme.colors.onBackground,
              onClick =
                actionRunCallback<PreviousChapterActionCallback>(
                  actionParametersOf(bookIdKey to bookId),
                ),
              modifier = GlanceModifier.defaultWeight(),
            )

            WidgetControlButton(
              size = 36.dp,
              icon = ImageProvider(rewindIcon),
              contentColor = GlanceTheme.colors.onBackground,
              onClick =
                actionRunCallback<RewindActionCallback>(
                  actionParametersOf(bookIdKey to bookId),
                ),
              modifier = GlanceModifier.defaultWeight(),
            )

            WidgetControlButton(
              icon =
                if (isPlaying) {
                  ImageProvider(R.drawable.media3_icon_pause)
                } else {
                  ImageProvider(R.drawable.media3_icon_play)
                },
              size = 48.dp,
              contentColor = GlanceTheme.colors.onBackground,
              onClick =
                actionRunCallback<PlayToggleActionCallback>(
                  actionParametersOf(bookIdKey to bookId),
                ),
              modifier = GlanceModifier.defaultWeight(),
            )

            WidgetControlButton(
              icon = ImageProvider(forwardIcon),
              size = 36.dp,
              contentColor = GlanceTheme.colors.onBackground,
              onClick =
                actionRunCallback<ForwardActionCallback>(
                  actionParametersOf(bookIdKey to bookId),
                ),
              modifier = GlanceModifier.defaultWeight(),
            )

            WidgetControlButton(
              icon = ImageProvider(R.drawable.media3_icon_next),
              size = 36.dp,
              contentColor = GlanceTheme.colors.onBackground,
              onClick =
                actionRunCallback<NextChapterActionCallback>(
                  actionParametersOf(bookIdKey to bookId),
                ),
              modifier = GlanceModifier.defaultWeight(),
            )
          }
        }
      }
    }
  }

  private fun GlanceModifier.safelyRunOnClick(context: Context) =
    provideAppLaunchIntent(context)
      ?.let { intent -> this.clickable(onClick = actionStartActivity(intent)) }
      ?: this

  companion object {
    val rewindIcon = R.drawable.media3_icon_rewind
    val forwardIcon = R.drawable.media3_icon_fast_forward

    val bookIdKey = ActionParameters.Key<String>("book_id")

    val coverPath = stringPreferencesKey("player_widget_key_cover")
    val bookId = stringPreferencesKey("player_widget_key_id")
    val title = stringPreferencesKey("player_widget_key_title")
    val chapterTitle = stringPreferencesKey("player_widget_key_chapter_title")

    val isPlaying = booleanPreferencesKey("player_widget_key_is_playing")
  }
}

class PlayToggleActionCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    safelyRun(
      playingItemId = parameters[bookIdKey] ?: return,
      context = context,
    ) { it.togglePlayPause() }
  }
}

class ForwardActionCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    safelyRun(
      playingItemId = parameters[bookIdKey] ?: return,
      context = context,
    ) { it.forward() }
  }
}

class RewindActionCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    safelyRun(
      playingItemId = parameters[bookIdKey] ?: return,
      context = context,
    ) { it.rewind() }
  }
}

class NextChapterActionCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    safelyRun(
      playingItemId = parameters[bookIdKey] ?: return,
      context = context,
    ) { it.nextTrack() }
  }
}

class PreviousChapterActionCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    safelyRun(
      playingItemId = parameters[bookIdKey] ?: return,
      context = context,
    ) { it.previousTrack() }
  }
}

private fun provideAppLaunchIntent(context: Context): Intent? =
  context
    .packageManager
    .getLaunchIntentForPackage(context.packageName)
    ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }

private suspend fun safelyRun(
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

    when (playbackController.providePlayingItem()) {
      null -> playbackController.prepareAndRun(playingItemId) { action(playbackController) }
      else -> action(playbackController)
    }
  } catch (ex: Exception) {
    Timber.w("Unable to run $action on $playingItemId due to $ex")
  }
}
