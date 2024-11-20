package org.grakovne.lissen.ui.widget

import android.content.Context
import android.os.PowerManager
import android.util.Base64
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.asFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerWidgetStateService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider
) : RunningComponent {

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        scope.launch {
            mediaRepository
                .playingBook
                .asFlow()
                .combine(mediaRepository.isPlaying.asFlow()) { book, isPlaying ->
                    val maybeCover = mediaProvider
                        .fetchBookCover(book.id)
                        .fold(
                            onSuccess = { it.readBytes() },
                            onFailure = { null }
                        )

                    PlayingItemState(
                        id = book.id,
                        title = book.title,
                        authorName = book.author,
                        isPlaying = isPlaying,
                        imageCover = maybeCover
                    )
                }
                .collect { updateWidgetState(it) }
        }
    }

    private suspend fun updateWidgetState(
        state: PlayingItemState
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)
        if (glanceIds.isEmpty() || isScreenOn().not()) return

        glanceIds
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[PlayerWidget.id] = state.id
                    prefs[PlayerWidget.encodedCover] = state.imageCover?.toBase64() ?: ""
                    prefs[PlayerWidget.title] = state.title
                    prefs[PlayerWidget.authorName] = state.authorName ?: ""
                    prefs[PlayerWidget.isPlaying] = state.isPlaying
                }
                PlayerWidget().update(context, glanceId)
            }
    }

    private fun isScreenOn(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }
}

data class PlayingItemState(
    val id: String,
    val title: String,
    val authorName: String?,
    val isPlaying: Boolean = false,
    val imageCover: ByteArray?
)



fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.DEFAULT)
