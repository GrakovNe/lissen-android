package org.grakovne.lissen.widget

import android.content.Context
import androidx.annotation.OptIn
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.widget.cover.PlayerCoverWidget
import org.grakovne.lissen.widget.state.PlayerStateWidget
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class PlayerWidgetStateService
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
  ) : RunningComponent {
    private val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
          CoroutineExceptionHandler { _, throwable ->
            Timber.e(throwable, "Widget state coroutine failed, ignoring")
          },
      )

    override fun onCreate() {
      scope.launch {
        combine(
          mediaRepository.playingBook,
          mediaRepository.isPlaying,
          mediaRepository.currentChapterIndex,
        ) { playingItem: DetailedItem?, isPlaying, chapterIndex: Int ->
          val chapterTitle = provideChapterTitle(playingItem, chapterIndex)

          val maybeCover =
            playingItem
              ?.id
              ?.let { provideCover(it) }

          PlayingItemState(
            id = playingItem?.id ?: "",
            title = playingItem?.title ?: "",
            chapterTitle = chapterTitle,
            isPlaying = isPlaying,
            coverFile = maybeCover,
          )
        }.collect { playingItemState ->
          updatePlayingItem(playingItemState)
        }
      }
    }

    @Volatile
    private var coverCache: Pair<String, File>? = null

    internal suspend fun provideCover(bookId: String): File? {
      coverCache
        ?.takeIf { it.first == bookId }
        ?.let { return it.second }

      val cover =
        mediaProvider
          .fetchBookCover(bookId)
          .fold(
            onSuccess = { it },
            onFailure = { null },
          )

      cover?.let { coverCache = bookId to it }
      return cover
    }

    private fun provideChapterTitle(
      item: DetailedItem?,
      chapterIndex: Int?,
    ): String? {
      if (item == null || chapterIndex == null) {
        return null
      }

      return when (chapterIndex in item.chapters.indices) {
        true -> item.chapters[chapterIndex].title
        false -> item.title
      }
    }

    private suspend fun updatePlayingItem(state: PlayingItemState) {
      updateWidgets(
        widget = PlayerStateWidget(),
        glanceIds = GlanceAppWidgetManager(context).getGlanceIds(PlayerStateWidget::class.java),
        state = state,
      )

      updateWidgets(
        widget = PlayerCoverWidget(),
        glanceIds = GlanceAppWidgetManager(context).getGlanceIds(PlayerCoverWidget::class.java),
        state = state,
      )
    }

    private suspend fun updateWidgets(
      widget: GlanceAppWidget,
      glanceIds: List<GlanceId>,
      state: PlayingItemState,
    ) {
      glanceIds.forEach { glanceId ->
        updateAppWidgetState(context, glanceId) { prefs ->
          when (widget) {
            is PlayerStateWidget -> {
              prefs[PlayerStateWidget.bookId] = state.id
              prefs[PlayerStateWidget.coverPath] = state.coverFile?.absolutePath ?: ""
              prefs[PlayerStateWidget.title] = state.title
              prefs[PlayerStateWidget.chapterTitle] = state.chapterTitle ?: ""
              prefs[PlayerStateWidget.isPlaying] = state.isPlaying
            }

            is PlayerCoverWidget -> {
              prefs[PlayerCoverWidget.bookId] = state.id
              prefs[PlayerCoverWidget.coverPath] = state.coverFile?.absolutePath ?: ""
              prefs[PlayerCoverWidget.isPlaying] = state.isPlaying
            }
          }
        }

        widget.update(context, glanceId)
      }
    }
  }

data class PlayingItemState(
  val id: String,
  val title: String,
  val chapterTitle: String?,
  val isPlaying: Boolean = false,
  val coverFile: File?,
)
