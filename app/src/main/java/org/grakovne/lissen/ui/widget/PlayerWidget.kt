package org.grakovne.lissen.ui.widget

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentWidth
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.R
import dagger.hilt.android.EntryPointAccessors
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
                val bookTitle = prefs[bookTitleKey] ?: "Nothing Playing"
                val bookAuthor = prefs[bookAuthorKey] ?: ""
                val isPlaying = prefs[isPlayingKey] ?: false

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.background)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row: Image with Text
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.media3_icon_play),
                            contentDescription = null,
                            modifier = GlanceModifier.size(80.dp)
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Column(
                            modifier = GlanceModifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = bookTitle,
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = bookAuthor,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    color = GlanceTheme.colors.onBackground
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(16.dp))

                    // Bottom Row: Controls centered with padding from edges
                    Row(
                        modifier = GlanceModifier.wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_skip_back_10),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PreviousChapterActionCallback>()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_previous),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PreviousChapterActionCallback>()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        WidgetControlButton(
                            icon = if (isPlaying) {
                                ImageProvider(R.drawable.media3_icon_pause)
                            } else {
                                ImageProvider(R.drawable.media3_icon_play)
                            },
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<PlayToggleActionCallback>()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_next),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<NextChapterActionCallback>()
                        )

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        WidgetControlButton(
                            icon = ImageProvider(R.drawable.media3_icon_skip_forward_30),
                            contentColor = GlanceTheme.colors.onBackground,
                            onClick = actionRunCallback<NextChapterActionCallback>()
                        )
                    }
                }
            }
        }

    }

    companion object {

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
