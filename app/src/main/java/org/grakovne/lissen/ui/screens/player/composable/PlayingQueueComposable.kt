package org.grakovne.lissen.ui.screens.player.composable

import android.view.ViewConfiguration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.viewmodel.PlayerViewModel

@Composable
fun PlayingQueueComposable(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val book by viewModel.book.observeAsState()
    val chapters = book?.chapters ?: emptyList()
    val currentTrackIndex by viewModel.currentChapterIndex.observeAsState(0)

    val playbackReady by viewModel.isPlaybackReady.observeAsState(false)
    val playingQueueExpanded by viewModel.playingQueueExpanded.observeAsState(false)

    val playingQueueHeight = remember { mutableIntStateOf(0) }
    val isFlinging = remember { mutableStateOf(false) }

    val expandFlingThreshold =
        remember { ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat() * 2 }

    val collapseFlingThreshold =
        remember { ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat() * 0.2 }

    val listState = rememberLazyListState()

    val fontSize by animateFloatAsState(
        targetValue = typography.titleMedium.fontSize.value * 1.25f,
        animationSpec = tween(durationMillis = 500),
        label = "playing_queue_font_size"
    )

    LaunchedEffect(currentTrackIndex) {
        if (!playingQueueExpanded) {
            scrollPlayingQueue(
                currentTrackIndex = currentTrackIndex,
                listState = listState,
                playbackReady = playbackReady,
                animate = true
            )
        }
    }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.player_screen_now_playing_title),
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Spacer(modifier = Modifier.padding(vertical = 4.dp))

        LazyColumn(
            modifier = Modifier
                .scrollable(
                    state = rememberScrollState(),
                    orientation = Orientation.Vertical,
                    enabled = playingQueueExpanded
                )
                .onSizeChanged { intSize ->
                    if (intSize.height != playingQueueHeight.intValue) {
                        playingQueueHeight.intValue = intSize.height

                        coroutineScope.launch {
                            if (!playingQueueExpanded) {
                                scrollPlayingQueue(
                                    currentTrackIndex = currentTrackIndex,
                                    listState = listState,
                                    playbackReady = playbackReady,
                                    animate = false
                                )
                            }
                        }
                    }
                }
                .nestedScroll(object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        return if (playingQueueExpanded) Offset.Zero else available
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (available.y < -expandFlingThreshold && !playingQueueExpanded) {
                            isFlinging.value = true
                            viewModel.expandPlayingQueue()
                            return available
                        }

                        if (available.y > collapseFlingThreshold && playingQueueExpanded) {
                            isFlinging.value = true
                            viewModel.collapsePlayingQueue()
                            return available
                        }
                        isFlinging.value = false
                        return available
                    }
                }),
            state = listState
        ) {
            itemsIndexed(chapters) { index, track ->
                PlaylistItemComposable(
                    track = track,
                    onClick = { viewModel.setChapter(index) },
                    isSelected = index == currentTrackIndex
                )

                if (index < chapters.size - 1) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
        }
    }
}

private suspend fun scrollPlayingQueue(
    currentTrackIndex: Int,
    listState: LazyListState,
    playbackReady: Boolean,
    animate: Boolean
) {
    val targetIndex = when (currentTrackIndex > 0) {
        true -> currentTrackIndex - 1
        false -> 0
    }

    when (animate && playbackReady) {
        true -> listState.animateScrollToItem(targetIndex)
        false -> listState.scrollToItem(targetIndex)
    }
}
