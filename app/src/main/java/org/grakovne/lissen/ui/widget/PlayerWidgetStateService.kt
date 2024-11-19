package org.grakovne.lissen.ui.widget

import android.content.Context
import android.os.PowerManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.asFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class PlayerWidgetStateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository
) : RunningComponent {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        scope.launch {
            mediaRepository
                .playingBook
                .asFlow()
                .combine(mediaRepository.isPlaying.asFlow()) { book, isPlaying -> book to isPlaying }
                .collect { (book, isPlaying) -> updateWidgetState(book, isPlaying) }
        }
    }

    private suspend fun updateWidgetState(book: DetailedItem?, isPlaying: Boolean) {
        if (book == null) return

        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)
        if (glanceIds.isEmpty() || isScreenOn().not()) return

        glanceIds
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[PlayerWidget.bookTitleKey] = book.title
                    prefs[PlayerWidget.bookAuthorKey] = book.author ?: ""
                    prefs[PlayerWidget.isPlayingKey] = isPlaying
                }
                PlayerWidget().update(context, glanceId)
            }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }
}
