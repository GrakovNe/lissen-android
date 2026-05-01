package org.grakovne.lissen.widget.cover

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.grakovne.lissen.widget.state.PlayerStateWidget
import timber.log.Timber
import java.io.File

class PlayerCoverWidget : GlanceAppWidget() {
  override val stateDefinition = PreferencesGlanceStateDefinition
  override val sizeMode = SizeMode.Exact

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
      val targetWidthPx = (size.width.value * density).toInt().coerceAtLeast(1)
      val targetHeightPx = (size.height.value * density).toInt().coerceAtLeast(1)

      val coverBitmap =
        try {
          maybeCoverFile
            ?.takeIf { it.exists() }
            ?.let {
              decodeSampledBitmapFromFile(
                path = it.absolutePath,
                reqWidthPx = targetWidthPx,
                reqHeightPx = targetHeightPx,
              )
            }
            ?: decodeSampledBitmapFromResource(
              context = context,
              resId = drawable.cover_fallback_png,
              reqWidthPx = targetWidthPx,
              reqHeightPx = targetHeightPx,
            )
        } catch (e: Exception) {
          decodeSampledBitmapFromResource(
            context = context,
            resId = drawable.cover_fallback_png,
            reqWidthPx = targetWidthPx,
            reqHeightPx = targetHeightPx,
          )
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

private fun decodeSampledBitmapFromFile(
  path: String,
  reqWidthPx: Int,
  reqHeightPx: Int,
): Bitmap {
  val bounds =
    BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
  BitmapFactory.decodeFile(path, bounds)

  val options =
    BitmapFactory.Options().apply {
      inSampleSize = calculateInSampleSize(bounds, reqWidthPx, reqHeightPx)
      inPreferredConfig = Bitmap.Config.RGB_565
      inDither = true
    }

  return BitmapFactory.decodeFile(path, options)
}

private fun decodeSampledBitmapFromResource(
  context: Context,
  resId: Int,
  reqWidthPx: Int,
  reqHeightPx: Int,
): Bitmap {
  val bounds =
    BitmapFactory.Options().apply {
      inJustDecodeBounds = true
    }
  BitmapFactory.decodeResource(context.resources, resId, bounds)

  val options =
    BitmapFactory.Options().apply {
      inSampleSize = calculateInSampleSize(bounds, reqWidthPx, reqHeightPx)
      inPreferredConfig = Bitmap.Config.RGB_565
      inDither = true
    }

  return BitmapFactory.decodeResource(context.resources, resId, options)
}

private fun calculateInSampleSize(
  options: BitmapFactory.Options,
  reqWidthPx: Int,
  reqHeightPx: Int,
): Int {
  val srcWidth = options.outWidth
  val srcHeight = options.outHeight
  var inSampleSize = 1

  if (srcHeight > reqHeightPx || srcWidth > reqWidthPx) {
    var halfHeight = srcHeight / 2
    var halfWidth = srcWidth / 2

    while (
      halfHeight / inSampleSize >= reqHeightPx &&
      halfWidth / inSampleSize >= reqWidthPx
    ) {
      inSampleSize *= 2
    }
  }

  return inSampleSize.coerceAtLeast(1)
}
