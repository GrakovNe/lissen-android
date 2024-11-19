package org.grakovne.lissen.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
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

class PlayerWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Используйте реальные данные книги и состояние плеера
            val prefs = currentState<Preferences>()
            val bookTitle = prefs[bookTitleKey] ?: "Название книги"
            val bookAuthor = prefs[bookAuthorKey] ?: "Автор книги"
            val isPlaying = prefs[isPlayingKey] ?: false

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .background(Color.Gray)
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
                            maxLines = 2
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

                    // Кнопка воспроизведения/паузы
                    Image(
                        provider = ImageProvider(androidx.media3.session.R.drawable.media3_icon_pause),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = GlanceModifier.size(36.dp)

                    )
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
