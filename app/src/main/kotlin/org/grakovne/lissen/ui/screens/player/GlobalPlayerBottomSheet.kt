package org.grakovne.lissen.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.launch
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.library.composables.MiniPlayerComposable
import org.grakovne.lissen.ui.screens.player.composable.ChaptersBottomSheet
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
          color = androidx.compose.ui.graphics.Color.Transparent,
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
      ModalBottomSheet(
        onDismissRequest = { showBottomSheet = false },
        sheetState = sheetState,
        shape = androidx.compose.ui.graphics.RectangleShape,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = null,
        modifier = Modifier.fillMaxSize(),
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

  Box(modifier = Modifier.fillMaxSize()) {
    // Dynamic Background
    playingBook?.let { book ->
      val context = LocalContext.current
      val imageRequest =
        remember(book.id) {
          ImageRequest
            .Builder(context)
            .data(book.id)
            .size(Size.ORIGINAL)
            .build()
        }

      val blurModifier =
        if (android.os.Build.VERSION.SDK_INT >= 31) {
          Modifier.blur(radius = 40.dp)
        } else {
          Modifier
        }

      AsyncImage(
        model = imageRequest,
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier
            .fillMaxSize()
            .then(blurModifier)
            .alpha(0.6f),
      )

      // Scrim
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
      )
    }

    // We control the chapters sheet visibility
    var showChaptersList by remember { mutableStateOf(false) }

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .systemBarsPadding()
          .padding(horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Drag Handle / Chevron
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(onClick = onCollapse),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          imageVector = Icons.Rounded.KeyboardArrowDown,
          contentDescription = "Close",
          colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)),
          contentScale = ContentScale.FillBounds,
          modifier = Modifier.size(width = 64.dp, height = 32.dp),
        )
      }

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
              onTitleClick = {
                playingBook?.let { book ->
                  navController.showPlayer(book.id, book.title, book.subtitle, false)
                  onCollapse()
                }
              },
              onChapterClick = { showChaptersList = true },
            )
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      AnimatedVisibility(
        visible = playingQueueExpanded.not(),
        enter = expandVertically(animationSpec = tween(400)),
        exit = shrinkVertically(animationSpec = tween(400)),
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
      Spacer(modifier = Modifier.weight(1f))

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

          if (showChaptersList) {
            val isOnline by playerViewModel.isOnline.collectAsState(initial = false)

            ChaptersBottomSheet(
              book = it,
              currentPosition = playerViewModel.totalPosition.value ?: 0.0,
              currentChapterIndex = playerViewModel.currentChapterIndex.value ?: 0,
              isOnline = isOnline,
              cachingModelView = cachingModelView,
              onChapterSelected = { chapter ->
                val currentChapterIndex = playerViewModel.currentChapterIndex.value
                val index = it.chapters.indexOf(chapter)

                if (index == currentChapterIndex) {
                  playerViewModel.togglePlayPause()
                } else {
                  playerViewModel.setChapter(chapter)
                }
                showChaptersList = false
              },
              onDismissRequest = { showChaptersList = false },
            )
          }
        }
      }
    }
  }
}
