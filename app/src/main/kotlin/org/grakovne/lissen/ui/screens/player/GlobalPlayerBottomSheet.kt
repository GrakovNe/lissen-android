package org.grakovne.lissen.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.ui.effects.WindowBlurEffect
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalPlayerBottomSheet(
  navController: AppNavigationService,
  imageLoader: ImageLoader,
  content: @Composable () -> Unit,
) {
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val libraryViewModel: LibraryViewModel = hiltViewModel()
  val settingsViewModel: SettingsViewModel = hiltViewModel()
  val cachingModelView: CachingModelView = hiltViewModel()

  val playingBook by playerViewModel.book.observeAsState()
  val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)

  // We control the sheet visibility
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showBottomSheet by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  // Container
  Box(modifier = Modifier.fillMaxSize()) {
    // Main App Content
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(bottom = if (playingBook != null && !showBottomSheet) 66.dp else 0.dp),
    ) {
      content()
    }

    // Mini Player (Bottom Bar)
    // Show only if we have a book and the sheet is NOT open
    AnimatedVisibility(
      visible = playingBook != null && !showBottomSheet,
      enter = slideInVertically { it } + expandVertically(),
      exit = slideOutVertically { it } + shrinkVertically(),
      modifier = Modifier.align(Alignment.BottomCenter),
    ) {
      playingBook?.let { book ->
        Surface(
          shadowElevation = 0.dp,
          tonalElevation = 0.dp,
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.navigationBarsPadding()) {
            // We modify MiniPlayer to accept a click action that opens the sheet
            GlobalMiniPlayer(
              book = book,
              imageLoader = imageLoader,
              playerViewModel = playerViewModel,
              onOpenPlayer = { showBottomSheet = true },
            )
          }
        }
      }
    }

    // Full Player Bottom Sheet
    if (showBottomSheet) {
      WindowBlurEffect()

      ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f),
      ) {
        PlayerContent(
          navController = navController,
          playerViewModel = playerViewModel,
          libraryViewModel = libraryViewModel,
          settingsViewModel = settingsViewModel,
          cachingModelView = cachingModelView,
          imageLoader = imageLoader,
          onCollapse = {
            scope.launch { sheetState.hide() }.invokeOnCompletion {
              if (!sheetState.isVisible) {
                showBottomSheet = false
              }
            }
          },
        )
      }
    }
  }
}

@Composable
fun GlobalMiniPlayer(
  book: DetailedItem,
  imageLoader: ImageLoader,
  playerViewModel: PlayerViewModel,
  onOpenPlayer: () -> Unit,
) {
  // We pass a dummy NavController or null since we use onContentClick
  // However, MiniPlayerComposable still requires a AppNavigationService in the signature.
  // We can construct a dummy one safely because it won't be used for the click action.
  val context = androidx.compose.ui.platform.LocalContext.current

  org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable(
    book = book,
    imageLoader = imageLoader,
    playerViewModel = playerViewModel,
    onContentClick = onOpenPlayer,
  )
}

@Composable
fun PlayerContent(
  navController: AppNavigationService,
  playerViewModel: PlayerViewModel,
  libraryViewModel: LibraryViewModel,
  settingsViewModel: SettingsViewModel,
  cachingModelView: CachingModelView,
  imageLoader: ImageLoader,
  onCollapse: () -> Unit,
) {
  val playingBook by playerViewModel.book.observeAsState()
  val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
  val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Drag Handle (optional, ModalBottomSheet adds one)

    Spacer(modifier = Modifier.height(16.dp))

    // Track Details
    AnimatedVisibility(
      visible = playingQueueExpanded.not(),
      enter = expandVertically(animationSpec = tween(400)),
      exit = shrinkVertically(animationSpec = tween(400)),
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!isPlaybackReady) {
          TrackDetailsPlaceholderComposable("Loading...", null)
        } else {
          TrackDetailsComposable(
            viewModel = playerViewModel,
            imageLoader = imageLoader,
            libraryViewModel = libraryViewModel,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!isPlaybackReady) {
          TrackControlPlaceholderComposable(
            modifier = Modifier,
            settingsViewModel = settingsViewModel,
          )
        } else {
          TrackControlComposable(
            viewModel = playerViewModel,
            modifier = Modifier,
            settingsViewModel = settingsViewModel,
          )
        }
      }
    }

    // Controls (Sleep timer, etc)
    Spacer(modifier = Modifier.height(16.dp))

    if (playingBook != null && isPlaybackReady) {
      playingBook?.let {
        NavigationBarComposable(
          book = it,
          playerViewModel = playerViewModel,
          contentCachingModelView = cachingModelView,
          settingsViewModel = settingsViewModel,
          navController = navController,
          libraryType = libraryViewModel.fetchPreferredLibraryType(),
        )
      }
    }
  }
}
