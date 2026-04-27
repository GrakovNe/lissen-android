package org.grakovne.lissen.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.ui.icons.Search
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.BookmarksComposable
import org.grakovne.lissen.ui.screens.player.composable.MediaDetailComposable
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.common.provideNowPlayingTitle
import org.grakovne.lissen.ui.screens.player.composable.fallback.PlayingQueueFallbackComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.NavigationBarPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  navController: AppNavigationService,
  imageLoader: ImageLoader,
  bookId: String,
  bookTitle: String,
  bookSubtitle: String?,
  playInstantly: Boolean,
) {
  val context = LocalContext.current

  val cachingModelView: CachingModelView = hiltViewModel()
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val libraryViewModel: LibraryViewModel = hiltViewModel()
  val settingsViewModel: SettingsViewModel = hiltViewModel()

  val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

  val playingBook by playerViewModel.book.observeAsState()
  val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
  val playingQueueExpanded by playerViewModel.playingQueueExpanded.observeAsState(false)
  val searchRequested by playerViewModel.searchRequested.observeAsState(false)
  val density = LocalDensity.current

  var itemDetailsSelected by remember { mutableStateOf(false) }
  var bookmarksSelected by remember { mutableStateOf(false) }
  var playerDragOffset by remember { mutableStateOf(0f) }
  var playerSwipeHandled by remember { mutableStateOf(false) }

  val playerSwipeThreshold = with(density) { 48.dp.toPx() }
  val maxPlayerDrag = with(density) { 64.dp.toPx() }
  val animatedPlayerDragOffset by animateFloatAsState(
    targetValue = playerDragOffset,
    animationSpec = tween(durationMillis = 180),
    label = "player_screen_vertical_drag",
  )

  val screenTitle =
    when (playingQueueExpanded) {
      true -> provideNowPlayingTitle(libraryViewModel.fetchPreferredLibraryType(), context)
      false -> stringResource(R.string.player_screen_title)
    }

  fun stepBack() {
    when {
      searchRequested -> playerViewModel.dismissSearch()
      playingQueueExpanded -> playerViewModel.collapsePlayingQueue()
      else -> navController.showLibrary(clearHistory = true)
    }
  }

  fun handlePlayerVerticalSwipe() {
    if (playerSwipeHandled) {
      playerDragOffset = 0f
      return
    }

    val hasChapters = playingBook?.chapters?.isNotEmpty() == true
    val dragOffset = playerDragOffset

    playerDragOffset = 0f

    when {
      abs(dragOffset) < playerSwipeThreshold -> {
        Unit
      }

      dragOffset < 0 && hasChapters && isPlaybackReady && !playingQueueExpanded -> {
        playerViewModel.expandPlayingQueue()
      }

      dragOffset > 0 -> {
        stepBack()
      }
    }
  }

  BackHandler(enabled = searchRequested || playingQueueExpanded || playInstantly) {
    stepBack()
  }

  LaunchedEffect(Unit) {
    bookId
      .takeIf { playingItemChanged(it, playingBook) || cachePolicyChanged(cachingModelView, playingBook) }
      ?.let {
        if (settingsViewModel.hasCredentials().not()) {
          navController.showLogin()
          return@LaunchedEffect
        }

        playerViewModel.preparePlayback(it)
      }

    if (playInstantly) {
      playerViewModel.prepareAndPlay()
    }
  }

  LaunchedEffect(playingQueueExpanded) {
    if (playingQueueExpanded.not()) {
      playerViewModel.dismissSearch()
    }
  }

  LaunchedEffect(playingBook) {
    playerViewModel.updateBookmarks()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        actions = {
          if (playingQueueExpanded) {
            AnimatedContent(
              targetState = searchRequested,
              label = "library_action_animation",
              transitionSpec = {
                fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                  fadeOut(animationSpec = keyframes { durationMillis = 150 })
              },
            ) { isSearchRequested ->
              when (isSearchRequested) {
                true -> {
                  ChapterSearchActionComposable(
                    onSearchRequested = { playerViewModel.updateSearch(it) },
                  )
                }

                false -> {
                  Row {
                    IconButton(
                      onClick = { playerViewModel.requestSearch() },
                      modifier = Modifier.padding(end = 4.dp),
                    ) {
                      Icon(
                        imageVector = Search,
                        contentDescription = null,
                      )
                    }
                  }
                }
              }
            }
          } else {
            Row {
              IconButton(
                onClick = {
                  if (isPlaybackReady) {
                    playerViewModel.updateBookmarks()
                    bookmarksSelected = true
                  }
                },
                modifier = Modifier.padding(end = 4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Bookmarks,
                  contentDescription = null,
                )
              }

              IconButton(
                onClick = { itemDetailsSelected = true },
                modifier = Modifier.padding(end = 4.dp),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Info,
                  contentDescription = null,
                )
              }
            }
          }
        },
        title = {
          Text(
            text = screenTitle,
            style = titleTextStyle,
            color = colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        navigationIcon = {
          IconButton(onClick = { stepBack() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = null,
              tint = colorScheme.onSurface,
            )
          }
        },
      )
    },
    bottomBar = {
      if (playingBook == null || isPlaybackReady.not()) {
        NavigationBarPlaceholderComposable(libraryType = libraryViewModel.fetchPreferredLibraryType())
      } else {
        playingBook
          ?.let {
            NavigationBarComposable(
              book = it,
              playerViewModel = playerViewModel,
              contentCachingModelView = cachingModelView,
              navController = navController,
              libraryType = libraryViewModel.fetchPreferredLibraryType(),
            )
          }
      }
    },
    modifier = Modifier.systemBarsPadding(),
    content = { innerPadding ->
      Column(
        modifier =
          Modifier
            .testTag("playerScreen")
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier =
            Modifier
              .graphicsLayer {
                translationY = animatedPlayerDragOffset
              }.pointerInput(playingQueueExpanded, isPlaybackReady, playingBook?.id) {
                detectVerticalDragGestures(
                  onVerticalDrag = { change, dragAmount ->
                    if (playingQueueExpanded) return@detectVerticalDragGestures
                    if (playerSwipeHandled) return@detectVerticalDragGestures

                    change.consume()
                    playerDragOffset = (playerDragOffset + dragAmount).coerceIn(-maxPlayerDrag, maxPlayerDrag)

                    if (
                      playerDragOffset <= -playerSwipeThreshold &&
                      playingBook?.chapters?.isNotEmpty() == true &&
                      isPlaybackReady
                    ) {
                      playerSwipeHandled = true
                      playerViewModel.expandPlayingQueue()
                      playerDragOffset = 0f
                    } else if (playerDragOffset >= playerSwipeThreshold) {
                      playerSwipeHandled = true
                      stepBack()
                      playerDragOffset = 0f
                    }
                  },
                  onDragEnd = {
                    handlePlayerVerticalSwipe()
                    playerSwipeHandled = false
                  },
                  onDragCancel = {
                    playerDragOffset = 0f
                    playerSwipeHandled = false
                  },
                )
              },
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AnimatedVisibility(
            visible = playingQueueExpanded.not(),
            enter = expandVertically(animationSpec = tween(400)),
            exit = shrinkVertically(animationSpec = tween(400)),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              if (!isPlaybackReady) {
                TrackDetailsPlaceholderComposable(bookTitle, bookSubtitle)
              } else {
                TrackDetailsComposable(
                  viewModel = playerViewModel,
                  imageLoader = imageLoader,
                  libraryViewModel = libraryViewModel,
                )
              }

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

          Spacer(modifier = Modifier.height(6.dp))

          when {
            isPlaybackReady.not() -> {
              PlayingQueuePlaceholderComposable(
                libraryViewModel = libraryViewModel,
                modifier = Modifier,
              )
            }

            playingBook?.chapters.isNullOrEmpty() -> {
              PlayingQueueFallbackComposable(
                libraryViewModel = libraryViewModel,
                modifier = Modifier,
              )
            }

            else -> {
              PlayingQueueComposable(
                libraryViewModel = libraryViewModel,
                cachingModelView = cachingModelView,
                viewModel = playerViewModel,
                modifier = Modifier,
              )
            }
          }
        }
      }
    },
  )

  if (itemDetailsSelected) {
    MediaDetailComposable(
      playingBook = playingBook,
      playingViewModel = playerViewModel,
      settingsViewModel = settingsViewModel,
      onDismissRequest = { itemDetailsSelected = false },
    )
  }

  if (bookmarksSelected) {
    BookmarksComposable(
      playerViewModel = playerViewModel,
      onDismissRequest = { bookmarksSelected = false },
    )
  }
}

@Composable
fun InfoRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  textValue: String,
) {
  Spacer(modifier = Modifier.height(8.dp))

  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = "$label: ",
      style = typography.bodyMedium,
      color = Color.Gray,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = textValue,
      style = typography.bodyMedium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private fun playingItemChanged(
  item: String,
  playingBook: DetailedItem?,
) = item != playingBook?.id

private fun cachePolicyChanged(
  cachingModelView: CachingModelView,
  playingBook: DetailedItem?,
) = cachingModelView.localCacheUsing() != playingBook?.localProvided
