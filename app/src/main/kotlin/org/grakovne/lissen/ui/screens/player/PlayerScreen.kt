package org.grakovne.lissen.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import coil3.ImageLoader
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.ui.adaptive.isWideLayout
import org.grakovne.lissen.ui.icons.Search
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.BookCover
import org.grakovne.lissen.ui.screens.player.composable.BookmarksComposable
import org.grakovne.lissen.ui.screens.player.composable.MediaDetailComposable
import org.grakovne.lissen.ui.screens.player.composable.NavigationBarComposable
import org.grakovne.lissen.ui.screens.player.composable.PlayingQueueComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackControlComposable
import org.grakovne.lissen.ui.screens.player.composable.TrackDetailsComposable
import org.grakovne.lissen.ui.screens.player.composable.common.provideNowPlayingTitle
import org.grakovne.lissen.ui.screens.player.composable.fallback.PlayingQueueFallbackComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.BookCoverPlaceholder
import org.grakovne.lissen.ui.screens.player.composable.placeholder.ChapterNumberPlaceholder
import org.grakovne.lissen.ui.screens.player.composable.placeholder.NavigationBarPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.PlayingQueuePlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackControlPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.placeholder.TrackDetailsPlaceholderComposable
import org.grakovne.lissen.ui.screens.player.composable.provideChapterNumberTitle
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.LibraryViewModel
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

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

  val twoPane = isWideLayout()

  val cachingModelView: CachingModelView = hiltViewModel()
  val playerViewModel: PlayerViewModel = hiltViewModel()
  val libraryViewModel: LibraryViewModel = hiltViewModel()
  val settingsViewModel: SettingsViewModel = hiltViewModel()

  val titleTextStyle = typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

  val playingBook by playerViewModel.book.collectAsState()
  val isPlaybackReady by playerViewModel.isPlaybackReady.collectAsState()
  val playingQueueExpanded by playerViewModel.playingQueueExpanded.collectAsState()
  val searchRequested by playerViewModel.searchRequested.collectAsState()
  val preparingError by playerViewModel.preparingError.collectAsState()

  val view = LocalView.current
  val bufferingAnnouncement = stringResource(R.string.a11y_buffering)
  val playbackErrorAnnouncement = stringResource(R.string.a11y_playback_error)

  LaunchedEffect(preparingError) {
    if (preparingError) {
      @Suppress("DEPRECATION")
      view.announceForAccessibility(playbackErrorAnnouncement)
    }
  }

  LaunchedEffect(isPlaybackReady) {
    if (isPlaybackReady.not()) {
      @Suppress("DEPRECATION")
      view.announceForAccessibility(bufferingAnnouncement)
    }
  }

  var itemDetailsSelected by remember { mutableStateOf(false) }
  var bookmarksSelected by remember { mutableStateOf(false) }

  val libraryType by libraryViewModel.preferredLibraryType.collectAsState()

  val screenTitle =
    when {
      playingQueueExpanded && twoPane.not() -> {
        provideNowPlayingTitle(libraryType, context)
      }

      else -> {
        stringResource(R.string.player_screen_title)
      }
    }

  fun stepBack() {
    when {
      searchRequested -> playerViewModel.dismissSearch()
      playingQueueExpanded -> playerViewModel.collapsePlayingQueue()
      playInstantly -> navController.showLibrary(clearHistory = true)
      else -> navController.goBack()
    }
  }

  BackHandler(enabled = searchRequested || playingQueueExpanded || playInstantly) {
    stepBack()
  }

  val lifecycle = LocalLifecycleOwner.current.lifecycle

  LaunchedEffect(Unit) {
    val needsPreparation =
      playingItemChanged(bookId, playingBook) || cachePolicyChanged(cachingModelView, playingBook)

    if (needsPreparation) {
      if (settingsViewModel.hasCredentials().not()) {
        navController.showLogin()
        return@LaunchedEffect
      }

      playerViewModel.clearPrepared()
    }

    lifecycle.withResumed {}

    if (needsPreparation) {
      playerViewModel.preparePlayback(bookId)
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
          val queueControlsVisible = playingQueueExpanded || twoPane
          val bookActionsVisible = playingQueueExpanded.not() || twoPane

          AnimatedContent(
            targetState = searchRequested,
            label = "library_action_animation",
            transitionSpec = {
              fadeIn(animationSpec = keyframes { durationMillis = 150 }) togetherWith
                fadeOut(animationSpec = keyframes { durationMillis = 150 })
            },
          ) { isSearchRequested ->
            when {
              isSearchRequested -> {
                ChapterSearchActionComposable(
                  onSearchRequested = { playerViewModel.updateSearch(it) },
                )
              }

              else -> {
                Row {
                  if (queueControlsVisible) {
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

                  if (bookActionsVisible) {
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
                      modifier =
                        Modifier
                          .padding(end = 4.dp)
                          .testTag("playerInfoButton"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                      )
                    }
                  }
                }
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
            modifier =
              Modifier
                .fillMaxWidth()
                .semantics { heading() },
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { stepBack() },
            modifier = Modifier.testTag("playerBackButton"),
          ) {
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
        NavigationBarPlaceholderComposable(libraryType = libraryType)
      } else {
        playingBook
          ?.let {
            NavigationBarComposable(
              book = it,
              playerViewModel = playerViewModel,
              contentCachingModelView = cachingModelView,
              navController = navController,
              libraryType = libraryType,
            )
          }
      }
    },
    modifier = Modifier.systemBarsPadding(),
    content = { innerPadding ->
      if (twoPane) {
        Row(
          modifier =
            Modifier
              .testTag("playerScreen")
              .padding(innerPadding)
              .fillMaxSize(),
        ) {
          PlayerArtworkAndControlsWide(
            isPlaybackReady = isPlaybackReady,
            playingBook = playingBook,
            bookTitle = bookTitle,
            playerViewModel = playerViewModel,
            libraryViewModel = libraryViewModel,
            imageLoader = imageLoader,
            settingsViewModel = settingsViewModel,
            modifier =
              Modifier
                .weight(0.45f)
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 12.dp),
          )

          PlayerQueueSection(
            isPlaybackReady = isPlaybackReady,
            playingBook = playingBook,
            libraryViewModel = libraryViewModel,
            cachingModelView = cachingModelView,
            playerViewModel = playerViewModel,
            forceExpanded = true,
            modifier =
              Modifier
                .weight(0.55f)
                .fillMaxHeight(),
          )
        }
      } else {
        Column(
          modifier =
            Modifier
              .testTag("playerScreen")
              .padding(innerPadding),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          AnimatedVisibility(
            visible = playingQueueExpanded.not(),
            enter = expandVertically(animationSpec = tween(400)),
            exit = shrinkVertically(animationSpec = tween(400)),
          ) {
            PlayerArtworkAndControls(
              isPlaybackReady = isPlaybackReady,
              bookTitle = bookTitle,
              bookSubtitle = bookSubtitle,
              playerViewModel = playerViewModel,
              imageLoader = imageLoader,
              libraryViewModel = libraryViewModel,
              settingsViewModel = settingsViewModel,
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          PlayerQueueSection(
            isPlaybackReady = isPlaybackReady,
            playingBook = playingBook,
            libraryViewModel = libraryViewModel,
            cachingModelView = cachingModelView,
            playerViewModel = playerViewModel,
          )
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
      navController = navController,
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
private fun PlayerArtworkAndControls(
  isPlaybackReady: Boolean,
  bookTitle: String,
  bookSubtitle: String?,
  playerViewModel: PlayerViewModel,
  imageLoader: ImageLoader,
  libraryViewModel: LibraryViewModel,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier,
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

@Composable
private fun PlayerArtworkAndControlsWide(
  isPlaybackReady: Boolean,
  playingBook: DetailedItem?,
  bookTitle: String,
  playerViewModel: PlayerViewModel,
  libraryViewModel: LibraryViewModel,
  imageLoader: ImageLoader,
  settingsViewModel: SettingsViewModel,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val currentChapterIndex by playerViewModel.currentChapterIndex.collectAsState()

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier.testTag("playerArtworkPane"),
  ) {
    BoxWithConstraints(
      modifier =
        Modifier
          .weight(1f, fill = false)
          .fillMaxWidth(),
      contentAlignment = Alignment.Center,
    ) {
      val side = minOf(maxWidth, maxHeight)

      if (side >= 110.dp) {
        if (isPlaybackReady) {
          BookCover(
            book = playingBook,
            imageLoader = imageLoader,
            modifier = Modifier.size(side),
          )
        } else {
          BookCoverPlaceholder(
            modifier = Modifier.size(side),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = playingBook?.title ?: bookTitle,
      style = typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      color = colorScheme.onBackground,
      textAlign = TextAlign.Center,
      overflow = TextOverflow.Ellipsis,
      maxLines = 1,
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
    )

    Spacer(modifier = Modifier.height(2.dp))

    if (isPlaybackReady) {
      Text(
        text =
          provideChapterNumberTitle(
            currentTrackIndex = currentChapterIndex,
            book = playingBook,
            libraryType = libraryViewModel.fetchPreferredLibraryType(),
            context = context,
          ),
        style = typography.bodyMedium,
        color = colorScheme.onBackground.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier =
          Modifier
            .fillMaxWidth()
            .testTag("playerChapterNumber"),
      )
    } else {
      ChapterNumberPlaceholder(modifier = Modifier.testTag("playerChapterNumber"))
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (isPlaybackReady) {
      TrackControlComposable(
        viewModel = playerViewModel,
        modifier = Modifier,
        settingsViewModel = settingsViewModel,
      )
    } else {
      TrackControlPlaceholderComposable(
        modifier = Modifier,
        settingsViewModel = settingsViewModel,
      )
    }
  }
}

@Composable
private fun PlayerQueueSection(
  isPlaybackReady: Boolean,
  playingBook: DetailedItem?,
  libraryViewModel: LibraryViewModel,
  cachingModelView: CachingModelView,
  playerViewModel: PlayerViewModel,
  modifier: Modifier = Modifier,
  forceExpanded: Boolean = false,
) {
  when {
    isPlaybackReady.not() -> {
      PlayingQueuePlaceholderComposable(
        libraryViewModel = libraryViewModel,
        modifier = modifier,
      )
    }

    playingBook?.chapters.isNullOrEmpty() -> {
      PlayingQueueFallbackComposable(
        libraryViewModel = libraryViewModel,
        modifier = modifier,
      )
    }

    else -> {
      PlayingQueueComposable(
        libraryViewModel = libraryViewModel,
        cachingModelView = cachingModelView,
        viewModel = playerViewModel,
        modifier = modifier,
        forceExpanded = forceExpanded,
      )
    }
  }
}

@Composable
fun InfoRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  textValue: String,
  onClick: (() -> Unit)? = null,
  testTag: String? = null,
) {
  Spacer(modifier = Modifier.height(8.dp))

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier
        .let { if (testTag != null) it.testTag(testTag) else it }
        .let { base ->
          when (onClick) {
            null -> {
              base
            }

            else -> {
              base.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) { onClick() }
            }
          }
        },
  ) {
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
      style =
        typography.bodyMedium.copy(
          textDecoration = if (onClick != null) TextDecoration.Underline else TextDecoration.None,
        ),
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
