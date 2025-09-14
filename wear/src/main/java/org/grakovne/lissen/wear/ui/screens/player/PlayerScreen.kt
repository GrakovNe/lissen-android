package org.grakovne.lissen.wear.ui.screens.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.VerticalPagerScaffold
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumeViewModel
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.VolumeScreen
import com.google.android.horologist.audio.ui.material3.components.actions.VolumeButtonWithBadge
import com.google.android.horologist.audio.ui.material3.components.toAudioOutputUi
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.media.ui.material3.components.animated.AnimatedMediaControlButtons
import com.google.android.horologist.media.ui.material3.components.animated.MarqueeTextMediaDisplay
import com.google.android.horologist.media.ui.state.model.TrackPositionUiModel
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.MediaProgress
import org.grakovne.lissen.lib.domain.PlayingChapter
import kotlin.time.Duration
import com.google.android.horologist.media.ui.material3.screens.player.PlayerScreen as HorologistPlayerScreen

enum class Pages {
  PLAYER,
  SETTINGS
}


@OptIn(ExperimentalHorologistApi::class)
@Composable
fun PlayerScreen() {
  val pagerState = rememberPagerState(
    pageCount = { Pages.entries.size },
    initialPage = Pages.PLAYER.ordinal
  )
  VerticalPagerScaffold(
    pagerState = pagerState
  ) {
    VerticalPager(
      state = pagerState,
      rotaryScrollableBehavior = null
    ) { page ->
      when (page) {
        Pages.PLAYER.ordinal -> PlayerContent()
        Pages.SETTINGS.ordinal -> SettingsContent()
      }
    }
  }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun PlayerContent(
  focusRequester: FocusRequester = FocusRequester(),
) {
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val playingBook = playerViewModel.playingBook.observeAsState()
  val trackPosition = TrackPositionUiModel.Actual(
    shouldAnimate = true,
    percent = .25f,
    duration = Duration.ZERO,
    position = Duration.ZERO
  )

  val volumeViewModel: VolumeViewModel = viewModel(factory = VolumeViewModel.Factory)
  val volumeUiState by volumeViewModel.volumeUiState.collectAsStateWithLifecycle()
  val audioOutput by volumeViewModel.audioOutput.collectAsStateWithLifecycle()
  var volumeDialogVisible by remember { mutableStateOf(false) }

  VolumeLevelIndicator(
    volumeUiState = { volumeUiState },
    displayIndicatorEvents = volumeViewModel.displayIndicatorEvents
  )

  Dialog(
    visible = volumeDialogVisible,
    onDismissRequest = { volumeDialogVisible = false }
  ) {
    VolumeScreen(volumeViewModel = volumeViewModel)
  }

  PlayerContent(
    modifier = Modifier
      .requestFocusOnHierarchyActive()
      .rotaryScrollable(
        volumeRotaryBehavior(
          volumeUiStateProvider = { volumeUiState },
          onRotaryVolumeInput = { newVolume -> volumeViewModel.setVolume(newVolume) },
        ),
        focusRequester = focusRequester,
      ),
    playingBook = playingBook.value,
    playingChapterIndex = 0,
    trackPosition = trackPosition,
    leftButton = {
      VolumeButtonWithBadge(
        onOutputClick = { volumeDialogVisible = true },
        audioOutputUi = audioOutput.toAudioOutputUi(),
        volumeUiState = volumeUiState
      )
    }
  )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun PlayerContent(
  modifier: Modifier = Modifier,
  playingBook: DetailedItem?,
  playingChapterIndex: Int,
  trackPosition: TrackPositionUiModel,
  leftButton: @Composable () -> Unit = {}
) {
  var playing by remember { mutableStateOf(false) }
  val playingChapter = playingBook?.chapters?.getOrNull(playingChapterIndex)

  HorologistPlayerScreen(
    modifier = modifier,
    mediaDisplay = {
      MarqueeTextMediaDisplay(
        title = playingBook?.title ?: "No book selected.",
        artist = playingChapter?.title
      )
    },
    controlButtons = {
      AnimatedMediaControlButtons(
        onPlayButtonClick = { playing = true },
        onPauseButtonClick = { playing = false },
        playPauseButtonEnabled = true,
        playing = playing,
        onSeekToPreviousButtonClick = { },
        seekToPreviousButtonEnabled = true,
        onSeekToNextButtonClick = {},
        seekToNextButtonEnabled = true,
        trackPositionUiModel = trackPosition
//      onSeekToPreviousRepeatableClick = {},
//      onSeekToPreviousRepeatableClickEnd: = {},
//      onSeekToNextRepeatableClick: = {},
//      onSeekToNextRepeatableClickEnd: = {},
      )
    },
    buttons = {
      Row {
        val modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.ExtraSmallButtonSize)
        val colors = IconButtonDefaults.filledTonalIconButtonColors()

        leftButton()

        FilledTonalIconButton(
          modifier = modifier,
          colors = colors,
          onClick = {}
        ) {
          Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Volume Settings")
        }
      }
    }
  )
}

@Composable
fun SettingsContent() {
  Box(modifier = Modifier.fillMaxSize()) { }
}

@OptIn(ExperimentalHorologistApi::class)
@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
  val book = DetailedItem(
    id = "",
    title = "Hitchhiker's Guide to the Galaxy",
    subtitle = "",
    author = "Douglas Adams",
    narrator = "",
    publisher = "",
    series = emptyList(),
    year = "",
    abstract = "",
    files = emptyList(),
    chapters = listOf(
      PlayingChapter(
        available = true,
        podcastEpisodeState = null,
        duration = 0.0,
        start = 0.0,
        end = 0.0,
        title = "Chapter 1",
        id = ""
      )
    ),
    progress = MediaProgress(currentTime = 0.0, isFinished = false, lastUpdate = 0),
    libraryId = "",
    localProvided = false,
    createdAt = 0,
    updatedAt = 0
  )
  AppScaffold {
    PlayerContent(
      playingBook = book,
      playingChapterIndex = 0,
      trackPosition = TrackPositionUiModel.Actual(
        shouldAnimate = true,
        percent = .25f,
        duration = Duration.ZERO,
        position = Duration.ZERO
      )
    )
  }
}