package org.grakovne.lissen.ui.screens.player.composable

import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.ui.components.withScrollbar
import org.grakovne.lissen.ui.screens.player.composable.common.provideNowPlayingTitle
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun PlayingQueueComposable(
  libraryType: LibraryType,
  cachingModelView: CachingModelView,
  viewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  forceExpanded: Boolean = false,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val book by viewModel.book.collectAsState()
  val searchToken by viewModel.searchToken.collectAsState()

  val showingChapters by remember {
    derivedStateOf {
      when (searchToken.isEmpty()) {
        true -> {
          book
            ?.chapters
            ?: emptyList()
        }

        false -> {
          book
            ?.chapters
            ?.filter { it.title.lowercase().contains(searchToken.lowercase()) }
            ?: emptyList()
        }
      }
    }
  }

  val currentTrackIndex by viewModel.currentChapterIndex.collectAsState()
  val currentTrackId by remember {
    derivedStateOf {
      book?.chapters?.getOrNull(currentTrackIndex)
    }
  }

  val bookId = book?.id ?: ""
  val cachedChapterIdsFlow =
    remember(bookId) {
      when (bookId.isEmpty()) {
        true -> flowOf(emptySet())
        false -> cachingModelView.provideCachedChapterIds(bookId).map { it.toSet() }
      }
    }
  val cachedChapterIds by cachedChapterIdsFlow.collectAsState(initial = emptySet())

  val playbackReady by viewModel.isPlaybackReady.collectAsState()
  val playingQueueExpanded by viewModel.playingQueueExpanded.collectAsState()

  val expanded = playingQueueExpanded || forceExpanded

  val density = LocalDensity.current

  var collapsedPlayingQueueHeight by remember { mutableIntStateOf(0) }

  val expandFlingThreshold =
    remember { ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat() * 2 }

  val collapseFlingThreshold =
    remember { ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat() * 0.3 }

  val listState = rememberLazyListState()

  val showScrollbar by remember {
    derivedStateOf {
      listState.isScrollInProgress
    }
  }

  val scrollbarAlpha by animateFloatAsState(
    targetValue = if (showScrollbar) 1f else 0f,
    animationSpec = tween(durationMillis = 300),
  )

  val fontSize by animateFloatAsState(
    targetValue = typography.titleMedium.fontSize.value * 1.25f,
    animationSpec = tween(durationMillis = 500),
    label = "playing_queue_font_size",
  )

  /**
   * Sheet-like gestures on top of the list: a fling up on the collapsed list expands it, a
   * fling or a pull down past the first item collapses it again. Nothing is intercepted in the
   * two-pane layout where the list is always expanded.
   */
  val sheetScrollConnection =
    remember(expanded, playingQueueExpanded, forceExpanded, density) {
      val collapseDragThresholdPx = with(density) { COLLAPSE_DRAG_THRESHOLD.toPx() }
      var pulledDownPx = 0f

      fun listAtTop() = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0

      fun collapsible() = playingQueueExpanded && forceExpanded.not()

      object : NestedScrollConnection {
        override fun onPreScroll(
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (available.y < 0f) pulledDownPx = 0f
          return if (expanded) Offset.Zero else available
        }

        override fun onPostScroll(
          consumed: Offset,
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          if (collapsible() && source == NestedScrollSource.UserInput && available.y > 0f && listAtTop()) {
            pulledDownPx += available.y

            if (pulledDownPx > collapseDragThresholdPx) {
              pulledDownPx = 0f
              viewModel.collapsePlayingQueue()
            }
          }

          return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
          pulledDownPx = 0f

          if (available.y < -expandFlingThreshold && !expanded) {
            viewModel.expandPlayingQueue()
            return available
          }

          if (available.y > collapseFlingThreshold && collapsible() && listAtTop()) {
            viewModel.collapsePlayingQueue()
            return available
          }

          return Velocity.Zero
        }
      }
    }

  LaunchedEffect(currentTrackIndex) {
    awaitFrame()
    scrollPlayingQueue(
      currentTrackIndex = currentTrackIndex,
      listState = listState,
      playbackReady = playbackReady,
      animate = true,
      playingQueueExpanded = playingQueueExpanded,
    )
  }

  Box(
    modifier =
      modifier
        .testTag("chapterList")
        .fillMaxSize(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .let {
            when (expanded) {
              true -> {
                it.withScrollbar(
                  state = listState,
                  color = colorScheme.onBackground.copy(alpha = scrollbarAlpha),
                  totalItems = showingChapters.size,
                  ignoreItems = emptyList(),
                )
              }

              false -> {
                it
              }
            }
          }.padding(horizontal = 16.dp),
    ) {
      PlayingQueueHeaderComposable(
        title = provideNowPlayingTitle(libraryType, context),
        textStyle = typography.titleMedium.copy(fontSize = fontSize.sp, fontWeight = FontWeight.SemiBold),
        expanded = playingQueueExpanded,
        draggable = forceExpanded.not(),
        onToggle = { viewModel.togglePlayingQueue() },
      )

      Spacer(modifier = Modifier.height(12.dp))

      LazyColumn(
        contentPadding =
          when (expanded) {
            true -> PaddingValues(bottom = 12.dp)
            false -> PaddingValues(bottom = with(density) { collapsedPlayingQueueHeight.toDp() })
          },
        modifier =
          Modifier
            .fillMaxHeight()
            .scrollable(
              state = rememberScrollState(),
              orientation = Orientation.Vertical,
              enabled = expanded,
            ).onGloballyPositioned {
              if (collapsedPlayingQueueHeight == 0) {
                collapsedPlayingQueueHeight = it.size.height
              }
            }.onSizeChanged { intSize ->
              if (intSize.height != collapsedPlayingQueueHeight) {
                coroutineScope.launch {
                  awaitFrame()
                  scrollPlayingQueue(
                    currentTrackIndex = currentTrackIndex,
                    listState = listState,
                    playbackReady = playbackReady,
                    animate = false,
                    playingQueueExpanded = playingQueueExpanded,
                  )
                }
              }
            }.nestedScroll(sheetScrollConnection),
        state = listState,
      ) {
        val maxDuration = showingChapters.maxOfOrNull { it.duration } ?: 0.0

        itemsIndexed(
          showingChapters,
          key = { _, chapter -> chapter.id },
        ) { index, chapter ->
          PlaylistItemComposable(
            track = chapter,
            onClick = { viewModel.setChapter(chapter) },
            isSelected = chapter.id == currentTrackId?.id,
            modifier = Modifier.wrapContentWidth(),
            maxDuration = maxDuration,
            isCached = chapter.id in cachedChapterIds,
          )

          if (index < showingChapters.size - 1) {
            HorizontalDivider(
              thickness = 1.dp,
              modifier =
                Modifier
                  .padding(start = 24.dp)
                  .padding(vertical = 8.dp),
            )
          }
        }
      }
    }
  }
}

private val COLLAPSE_DRAG_THRESHOLD = 96.dp

private suspend fun scrollPlayingQueue(
  currentTrackIndex: Int,
  listState: LazyListState,
  playbackReady: Boolean,
  animate: Boolean,
  playingQueueExpanded: Boolean,
) {
  if (playingQueueExpanded) {
    return
  }

  val targetIndex =
    when (currentTrackIndex > 0) {
      true -> currentTrackIndex - 1
      false -> 0
    }

  when (animate && playbackReady) {
    true -> listState.animateScrollToItem(targetIndex)
    false -> listState.scrollToItem(targetIndex)
  }
}
