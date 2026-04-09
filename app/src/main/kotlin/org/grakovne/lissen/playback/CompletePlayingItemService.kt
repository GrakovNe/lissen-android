package org.grakovne.lissen.playback

import androidx.annotation.OptIn
import androidx.lifecycle.asFlow
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.grakovne.lissen.common.RunningComponent
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.content.cache.common.round
import org.grakovne.lissen.lib.domain.DetailedItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
@OptIn(UnstableApi::class)
class CompletePlayingItemService
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val mediaProvider: LissenMediaProvider,
  ) : RunningComponent {
    private var delayedJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
      scope.launch {
        combine(
          mediaRepository.playingBook
            .asFlow()
            .distinctUntilChanged()
            .filterNotNull(),
          mediaRepository.currentChapterIndex
            .asFlow()
            .distinctUntilChanged()
            .filterNotNull(),
          mediaRepository.isPlaying
            .asFlow()
            .filterNotNull()
            .distinctUntilChanged(),
        ) { playingItem: DetailedItem, currentTrackIndex: Int, _: Boolean ->
          TrackChangedState(playingItem, currentTrackIndex)
        }.collectLatest { currentState ->
          delayedJob?.cancel()
          delayedJob = completePlayingItem(currentState)
        }
      }
    }

    private fun completePlayingItem(state: TrackChangedState): Job =
      scope.launch {
        val latestTrack = state.currentTrackIndex == state.item.chapters.size - 1
        val completedPlaying =
          mediaRepository.totalPosition.value?.roundToInt() ==
            state.item.chapters
              .sumOf { it.duration }
              .roundToInt()

        if (latestTrack && completedPlaying) {
          mediaProvider.completeProgress(state.item.id)
        }
      }
  }

data class TrackChangedState(
  val item: DetailedItem,
  val currentTrackIndex: Int,
)
