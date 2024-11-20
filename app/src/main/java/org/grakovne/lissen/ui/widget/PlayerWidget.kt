package org.grakovne.lissen.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentWidth
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.R
import dagger.hilt.android.EntryPointAccessors
import org.grakovne.lissen.R.drawable
import org.grakovne.lissen.playback.MediaRepositoryEntryPoint

class PlayerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(
                colors = ColorProviders(
                    light = lightColorScheme(
                        background = Color(0xFFFAFAFA)
                    ),
                    dark = darkColorScheme()
                )
            ) {
                val prefs = currentState<Preferences>()
                val cover = prefs[encodedCover]?.toBitmap()?.let { ImageProvider(it) }
                val bookTitle = prefs[title] ?: "Nothing Playing"
                val bookAuthor = prefs[authorName] ?: ""
                val isPlaying = prefs[isPlaying] ?: false

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.background)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = cover ?: ImageProvider(drawable.cover_fallback),
                            contentDescription = null,
                            modifier = GlanceModifier.size(80.dp)
                        )

                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(start = 24.dp)
                        ) {
                            Text(
                                text = bookAuthor,
                                style = TextStyle(
                                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )

                            Text(
                                text = bookTitle,
                                style = TextStyle(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = GlanceModifier
                            .padding(top = 16.dp)
                            .wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WidgetControlButton(
                            size = 36.dp,
                            icon = ImageProvider(R.drawable.media3_icon_skip_back_10),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PreviousChapterActionCallback>(),
                            modifier = GlanceModifier.padding(end = 24.dp)
                        )

                        WidgetControlButton(
                            size = 48.dp,
                            icon = ImageProvider(R.drawable.media3_icon_previous),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PreviousChapterActionCallback>(),
                            modifier = GlanceModifier.padding(end = 16.dp)
                        )

                        WidgetControlButton(
                            icon = if (isPlaying) {
                                ImageProvider(R.drawable.media3_icon_pause)
                            } else {
                                ImageProvider(R.drawable.media3_icon_play)
                            },
                            size = 48.dp,
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PlayToggleActionCallback>(),
                            modifier = GlanceModifier.padding(end = 16.dp)
                        )

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_next),
                            size = 48.dp,
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<NextChapterActionCallback>(),
                            modifier = GlanceModifier.padding(end = 24.dp)
                        )

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_skip_forward_30),
                            size = 36.dp,
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<NextChapterActionCallback>(),
                            modifier = GlanceModifier
                        )
                    }
                }
            }
        }

    }

    companion object {
        val encodedCover = stringPreferencesKey("player_widget_key_cover")
        val id = stringPreferencesKey("player_widget_key_id")
        val title = stringPreferencesKey("player_widget_key_title")
        val authorName = stringPreferencesKey("player_widget_key_author_name")
        val isPlaying = booleanPreferencesKey("player_widget_key_is_playing")
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

fun String.toBitmap(): Bitmap? {
    val bytes = Base64.decode(this, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
