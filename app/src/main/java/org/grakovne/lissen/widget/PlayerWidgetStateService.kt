package org.grakovne.lissen.widget

import android.content.Context
import android.os.PowerManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.asFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.common.toBase64
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
            mediaRepository.playingBook.asFlow().collect { book ->
                val maybeCover = mediaProvider
                    .fetchBookCover(book.id)
                    .fold(
                        onSuccess = { it.readBytes() },
                        onFailure = { null }
                    )

                val chapterTitle = mediaRepository
                    .currentChapterIndex
                    .value
                    ?.let { book.chapters[it] }
                    ?.title

                val playingItemState = PlayingItemState(
                    id = book.id,
                    title = book.title,
                    chapterTitle = chapterTitle,
                    isPlaying = mediaRepository.isPlaying.value ?: false,
                    imageCover = maybeCover
                )

                updatePlayingItem(playingItemState)
            }
        }

        scope.launch {
            mediaRepository.isPlaying.asFlow().collect { isPlaying ->
                updatePlayingState(isPlaying)
            }
        }

        scope.launch {
            mediaRepository.currentChapterIndex.asFlow().collect { chapterIndex ->

                val book = mediaRepository.playingBook.value ?: return@collect

                val chapterTitle = book
                    .chapters[chapterIndex]
                    .title

                updateChapterTitle(chapterTitle)
            }
        }
    }

    private suspend fun updateChapterTitle(
        title: String
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)

        glanceIds
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[PlayerWidget.chapterTitle] = title
                }
                PlayerWidget().update(context, glanceId)
            }
    }

    private suspend fun updatePlayingState(
        isPlaying: Boolean
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)

        glanceIds
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[PlayerWidget.isPlaying] = isPlaying
                }
                PlayerWidget().update(context, glanceId)
            }
    }

    private suspend fun updatePlayingItem(
        state: PlayingItemState
    ) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(PlayerWidget::class.java)

        glanceIds
            .forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[PlayerWidget.bookId] = state.id
                    prefs[PlayerWidget.encodedCover] = state.imageCover?.toBase64() ?: ""
                    prefs[PlayerWidget.title] = state.title
                    prefs[PlayerWidget.chapterTitle] = state.chapterTitle ?: ""
                    prefs[PlayerWidget.isPlaying] = state.isPlaying
                }
                PlayerWidget().update(context, glanceId)
            }
    }
}

data class PlayingItemState(
    val id: String,
    val title: String,
    val chapterTitle: String?,
    val isPlaying: Boolean = false,
    val imageCover: ByteArray?
)
