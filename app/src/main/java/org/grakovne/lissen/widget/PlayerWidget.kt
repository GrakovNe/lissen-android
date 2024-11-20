package org.grakovne.lissen.widget

import android.content.Context
import android.content.Intent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.glance.action.actionParametersOf
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
import org.grakovne.lissen.common.clip
import org.grakovne.lissen.common.fromBase64
import org.grakovne.lissen.playback.WidgetPlaybackControllerEntryPoint
import org.grakovne.lissen.ui.theme.LightBackground
import org.grakovne.lissen.widget.PlayerWidget.Companion.bookIdKey

class PlayerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(
                colors = ColorProviders(
                    light = lightColorScheme(
                        background = LightBackground
                    ),
                    dark = darkColorScheme()
                )
            ) {
                val prefs = currentState<Preferences>()
                val cover = prefs[encodedCover]?.fromBase64()
                val bookId = prefs[bookId] ?: ""
                val bookTitle = prefs[title] ?: "Nothing Playing"
                val chapterTitle = prefs[chapterTitle] ?: ""

                val isPlaying = prefs[isPlaying] ?: false

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.background)
                        .padding(16.dp)
                        .clickable(onClick = actionRunCallback<RunLissenActionCallback>()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coverImageProvider = cover
                            ?.clip(context, 16.dp)
                            ?.let { ImageProvider(it) }
                            ?: ImageProvider(drawable.cover_fallback)

                        Image(
                            provider = coverImageProvider,
                            contentDescription = null,
                            modifier = GlanceModifier.size(80.dp)
                        )

                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(start = 24.dp)
                        ) {
                            Text(
                                text = bookTitle,
                                style = TextStyle(
                                    fontSize = MaterialTheme.typography.titleSmall.fontSize,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )

                            Text(
                                text = chapterTitle,
                                style = TextStyle(
                                    fontSize = MaterialTheme.typography.titleLarge.fontSize,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 2
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
                            onClick = actionRunCallback<RewindActionCallback>(
                                actionParametersOf(bookIdKey to bookId)
                            ),
                            modifier = GlanceModifier.padding(end = 24.dp)
                        )

                        WidgetControlButton(
                            size = 48.dp,
                            icon = ImageProvider(R.drawable.media3_icon_previous),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PreviousChapterActionCallback>(
                                actionParametersOf(bookIdKey to bookId)
                            ),
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
                            onClick = actionRunCallback<PlayToggleActionCallback>(
                                actionParametersOf(bookIdKey to bookId)
                            ),
                            modifier = GlanceModifier.padding(end = 16.dp)
                        )

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_next),
                            size = 48.dp,
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<NextChapterActionCallback>(
                                actionParametersOf(bookIdKey to bookId)
                            ),
                            modifier = GlanceModifier.padding(end = 24.dp)
                        )

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_skip_forward_30),
                            size = 36.dp,
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<ForwardActionCallback>(
                                actionParametersOf(bookIdKey to bookId)
                            ),
                            modifier = GlanceModifier
                        )
                    }
                }
            }
        }
    }

    companion object {

        val bookIdKey = ActionParameters.Key<String>("book_id")

        val encodedCover = stringPreferencesKey("player_widget_key_cover")
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
        parameters: ActionParameters
    ) {
        safelyRun(
            playingItemId = parameters[bookIdKey] ?: return,
            context = context
        ) { it.togglePlayPause() }
    }
}

class ForwardActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        safelyRun(
            playingItemId = parameters[bookIdKey] ?: return,
            context = context
        ) { it.forward() }
    }
}

class RewindActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        safelyRun(
            playingItemId = parameters[bookIdKey] ?: return,
            context = context
        ) { it.rewind() }
    }
}

class NextChapterActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        safelyRun(
            playingItemId = parameters[bookIdKey] ?: return,
            context = context
        ) { it.nextTrack() }
    }
}

class PreviousChapterActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        safelyRun(
            playingItemId = parameters[bookIdKey] ?: return,
            context = context
        ) { it.previousTrack() }
    }
}

class RunLissenActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val launchIntent = context
            .packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: return

        context.startActivity(launchIntent.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}

private suspend fun safelyRun(
    playingItemId: String,
    context: Context,
    action: (WidgetPlaybackController) -> Unit
) {
    val playbackController = EntryPointAccessors
        .fromApplication(
            context = context.applicationContext,
            entryPoint = WidgetPlaybackControllerEntryPoint::class.java
        )
        .widgetPlaybackController()

    when (playbackController.providePlayingItem()) {
        null -> playbackController.prepareAndRun(playingItemId) { action(playbackController) }
        else -> action(playbackController)
    }
}
