package org.grakovne.lissen.widget

import android.content.Context
import androidx.annotation.OptIn
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.common.toBase64
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.playback.MediaRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlayerWidgetStateService
  @Inject
  constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
  ) : RunningComponent {
    private var playingBookId: String? = null
    private var cachedCover: ByteArray? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
      scope.launch {
        combine(
          mediaRepository.playingBook.asFlow().distinctUntilChanged(),
          mediaRepository.isPlaying
            .asFlow()
            .filterNotNull()
            .distinctUntilChanged(),
          mediaRepository.currentChapterIndex.asFlow().distinctUntilChanged(),
        ) { book: DetailedItem?, isPlaying, chapterIndex: Int? ->
          val chapterTitle = provideChapterTitle(book, chapterIndex)

          val maybeCover =
            when (book) {
              null -> null
              else ->
                when {
                  playingBookId != book.id || cachedCover == null -> {
                    mediaProvider
                      .fetchBookCover(book.id)
                      .fold(
                        onSuccess = { buffer ->
                          val image = buffer.readByteArray()

                          cachedCover = image
                          playingBookId = book.id

                          image
                        },
                        onFailure = { null },
                      )
                  }

                  else -> cachedCover
                }
            }

          PlayingItemState(
            id = book?.id ?: "",
            title = book?.title ?: "",
            chapterTitle = chapterTitle,
            isPlaying = isPlaying,
            imageCover = maybeCover,
          )
        }.collect { playingItemState ->
          updatePlayingItem(playingItemState)
        }
      }
    }

    private fun provideChapterTitle(
      book: DetailedItem?,
      chapterIndex: Int?,
    ): String? {
      if (null == book || null == chapterIndex) {
        return null
      }

      return when (chapterIndex in book.chapters.indices) {
        true -> book.chapters[chapterIndex].title
        false -> book.title
      }
    }

    private suspend fun updatePlayingItem(state: PlayingItemState) {
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
  val imageCover: ByteArray?,
)
