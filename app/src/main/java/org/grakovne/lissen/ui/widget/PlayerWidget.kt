package org.grakovne.lissen.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.android.EntryPointAccessors
import org.grakovne.lissen.playback.MediaRepositoryEntryPoint

class PlayerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val bookTitle = prefs[bookTitleKey] ?: ""
            val bookAuthor = prefs[bookAuthorKey] ?: ""
            val isPlaying = prefs[isPlayingKey] ?: false

            val previousIcon = createColoredBitmap(
                context,
                androidx.media3.session.R.drawable.media3_icon_previous,
                colorScheme.onBackground.toArgb()
            )

            val nextIcon = createColoredBitmap(
                context,
                androidx.media3.session.R.drawable.media3_icon_next,
                colorScheme.onBackground.toArgb()
            )

            val playIcon = createColoredBitmap(
                context,
                androidx.media3.session.R.drawable.media3_icon_play,
                colorScheme.onBackground.toArgb()
            )

            val pauseIcon = createColoredBitmap(
                context,
                androidx.media3.session.R.drawable.media3_icon_pause,
                colorScheme.onBackground.toArgb()
            )

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Column(
                        modifier = GlanceModifier.defaultWeight()
                    ) {
                        Text(
                            text = bookTitle,
                            style = TextStyle(
                                fontSize = 16.sp
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = bookAuthor,
                            style = TextStyle(
                                fontSize = 14.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = GlanceModifier.width(16.dp))

                    Image(
                        provider = ImageProvider(previousIcon),
                        contentDescription = "Previous Chapter",
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<PreviousChapterActionCallback>())

                    )
                    Image(
                        provider = when (isPlaying) {
                            true -> ImageProvider(pauseIcon)
                            false -> ImageProvider(playIcon)
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<PlayToggleActionCallback>())
                    )
                    Image(
                        provider = ImageProvider(nextIcon),
                        contentDescription = "Next Chapter",
                        modifier = GlanceModifier
                            .size(36.dp)
                            .clickable(actionRunCallback<NextChapterActionCallback>())
                    )
                }
            }
        }
    }

    companion object {

        fun createColoredBitmap(context: Context, drawableRes: Int, color: Int): Bitmap {
            val drawable = ContextCompat.getDrawable(context, drawableRes)
                ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            drawable.draw(canvas)
            return bitmap
        }

        val bookTitleKey = stringPreferencesKey("bookTitle")
        val bookAuthorKey = stringPreferencesKey("bookAuthor")
        val isPlayingKey = booleanPreferencesKey("isPlaying")
    }
}

class PlayToggleActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val mediaRepository = EntryPointAccessors
            .fromApplication(
                context = context.applicationContext,
                entryPoint = MediaRepositoryEntryPoint::class.java
            )
            .mediaRepository()

        mediaRepository.togglePlayPause()
    }
}

class NextChapterActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val mediaRepository = EntryPointAccessors
            .fromApplication(
                context = context.applicationContext,
                entryPoint = MediaRepositoryEntryPoint::class.java
            )
            .mediaRepository()

        mediaRepository.nextTrack()
    }
}

class PreviousChapterActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val mediaRepository = EntryPointAccessors
            .fromApplication(
                context = context.applicationContext,
                entryPoint = MediaRepositoryEntryPoint::class.java
            )
            .mediaRepository()

        mediaRepository.nextTrack()
    }
}
