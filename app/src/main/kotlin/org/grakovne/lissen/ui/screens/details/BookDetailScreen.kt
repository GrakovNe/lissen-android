package org.grakovne.lissen.ui.screens.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.launch
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.content.cache.persistent.CacheState
import org.grakovne.lissen.lib.domain.CacheStatus
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.components.AsyncShimmeringImage
import org.grakovne.lissen.ui.components.DownloadProgressIcon
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.navigation.AppNavigationService
import org.grakovne.lissen.ui.screens.player.composable.DownloadsComposable
import org.grakovne.lissen.ui.screens.player.composable.PlaylistItemComposable
import org.grakovne.lissen.viewmodel.CachingModelView
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
  navController: AppNavigationService,
  imageLoader: ImageLoader,
  bookId: String,
  bookTitle: String,
  playerViewModel: PlayerViewModel = hiltViewModel(),
  settingsViewModel: SettingsViewModel = hiltViewModel(),
  cachingModelView: CachingModelView = hiltViewModel(),
) {
  val context = LocalContext.current
  val view = LocalView.current
  val playingBook by playerViewModel.book.observeAsState()
  val bookDetail by playerViewModel.getBookFlow(bookId).collectAsState(initial = null)
  val isPlaybackReady by playerViewModel.isPlaybackReady.observeAsState(false)
  val isPlaying by playerViewModel.isPlaying.observeAsState(false)
  val preferredLibrary by settingsViewModel.preferredLibrary.observeAsState()
  val isOnline by playerViewModel.isOnline.collectAsState(initial = false)

  val cacheProgress: CacheState by cachingModelView.getProgress(bookId).collectAsState(initial = CacheState(CacheStatus.Idle))
  val hasDownloadedChapters by cachingModelView.hasDownloadedChapters(bookId).observeAsState(false)
  var downloadsExpanded by remember { mutableStateOf(false) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  LaunchedEffect(bookId) {
    timber.log.Timber.d("BookDetailScreen: Launched with bookId $bookId")
  }

  LaunchedEffect(bookDetail) {
    timber.log.Timber.d("BookDetailScreen: bookDetail changed to ${bookDetail?.id}, title: ${bookDetail?.title}")
  }

  LaunchedEffect(bookId) {
    if (playingBook?.id != bookId) {
      playerViewModel.preparePlayback(bookId)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {},
        navigationIcon = {
          IconButton(onClick = { navController.back() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = colorScheme.onSurface,
            )
          }
        },
        actions = {
          IconButton(onClick = { downloadsExpanded = true }) {
            DownloadProgressIcon(
              cacheState = cacheProgress,
              color = colorScheme.onSurface,
            )
          }
        },
      )
    },
    modifier = Modifier.systemBarsPadding(),
  ) { innerPadding ->
    if (bookDetail == null) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      val book = bookDetail!!
      val chapters = book.chapters
      val maxDuration = chapters.maxOfOrNull { it.duration } ?: 0.0

      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        item {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cover Image
            val imageRequest =
              remember(book.id) {
                ImageRequest
                  .Builder(context)
                  .data(book.id)
                  .build()
              }

            Box(
              modifier =
                Modifier
                  .padding(top = 16.dp, bottom = 16.dp)
                  .height(260.dp) // Large cover
                  .aspectRatio(1f)
                  .shadow(12.dp, RoundedCornerShape(12.dp))
                  .clip(RoundedCornerShape(12.dp)),
            ) {
              AsyncShimmeringImage(
                imageRequest = imageRequest,
                imageLoader = imageLoader,
                contentDescription = "${book.title} cover",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
                error = painterResource(R.drawable.cover_fallback),
              )
            }

            // Title & Author
            Text(
              text = book.title,
              style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = colorScheme.onSurface,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 24.dp),
            )

            book.author?.takeIf { it.isNotBlank() }?.let {
              Text(
                text = stringResource(R.string.book_detail_author_pattern, it),
                style = typography.titleMedium,
                color = colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
              )
            }

            book.narrator?.takeIf { it.isNotEmpty() }?.let {
              Text(
                text = stringResource(R.string.book_detail_narrator_pattern, it),
                style = typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp),
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
              onClick = {
                withHaptic(view) {
                  if (playingBook?.id == bookId && isPlaying) {
                    playerViewModel.togglePlayPause()
                  } else {
                    if (!playerViewModel.isPlaying.value!!) {
                      playerViewModel.togglePlayPause()
                    }
                  }
                }
              },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp)
                  .height(56.dp),
            ) {
              val isCurrentBookPlaying = playingBook?.id == bookId && isPlaying

              val icon = if (isCurrentBookPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
              Icon(imageVector = icon, contentDescription = null, tint = colorScheme.onPrimary)

              Spacer(modifier = Modifier.width(8.dp))
              val isCompleted = book.progress?.isFinished == true
              val isStarted = (book.progress?.currentTime ?: 0.0) > 0

              val buttonText =
                when {
                  isCurrentBookPlaying -> stringResource(R.string.book_detail_action_pause)
                  isCompleted -> stringResource(R.string.book_detail_action_listen_again)
                  isStarted -> stringResource(R.string.book_detail_action_resume)
                  else -> stringResource(R.string.book_detail_action_start)
                }

              Text(
                text = buttonText,
                style = typography.titleMedium,
                color = colorScheme.onPrimary,
              )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Details
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
              val totalDuration = chapters.sumOf { it.duration }
              val currentPosition = book.progress?.currentTime ?: 0.0
              val isStarted = currentPosition > 0 && book.progress?.isFinished != true

              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                // Published Tile
                BookInfoTile(
                  label = stringResource(R.string.book_detail_published),
                  modifier =
                    Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                ) {
                  Text(
                    text = book.year ?: "-",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                  )
                }

                // Length/Remaining Tile
                val durationLabel =
                  if (isStarted) {
                    stringResource(
                      R.string.book_detail_remaining,
                    )
                  } else {
                    stringResource(R.string.book_detail_length)
                  }
                var durationSeconds =
                  if (isStarted) {
                    (totalDuration - currentPosition).toLong()
                  } else {
                    totalDuration.toLong()
                  }

                val hours = durationSeconds / 3600
                val minutes = (durationSeconds % 3600) / 60

                BookInfoTile(
                  label = durationLabel,
                  modifier =
                    Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                ) {
                  if (hours > 0) {
                    Text(
                      text = stringResource(R.string.duration_hours, hours),
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                      color = MaterialTheme.colorScheme.onSurface,
                      textAlign = TextAlign.Center,
                    )
                    if (minutes > 0) {
                      Text(
                        text = stringResource(R.string.duration_minutes, minutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                      )
                    }
                  } else {
                    Text(
                      text = stringResource(R.string.duration_minutes, minutes),
                      style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                      color = MaterialTheme.colorScheme.onSurface,
                      textAlign = TextAlign.Center,
                    )
                  }
                }

                // Publisher Tile
                BookInfoTile(
                  label = stringResource(R.string.playing_item_details_publisher),
                  modifier =
                    Modifier
                      .weight(1f)
                      .fillMaxHeight(),
                ) {
                  Text(
                    text = book.publisher ?: "-",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }

              book.abstract?.takeIf { it.isNotEmpty() }?.let {
                Spacer(modifier = Modifier.height(16.dp))
                ExpandableDescription(it)
              }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
              text = stringResource(R.string.player_screen_chapter_list_navigation_library),
              style = typography.titleMedium.copy(fontWeight = FontWeight.Black),
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
          }
        }

        itemsIndexed(chapters) { index, chapter ->
          val isCached by cachingModelView.provideCacheState(book.id, chapter.id).observeAsState(false)
          val isPlayingChapter = playingBook?.id == bookId && playerViewModel.currentChapterIndex.value == index

          PlaylistItemComposable(
            track = chapter,
            onClick = {
              if (playingBook?.id == bookId) {
                playerViewModel.setChapter(chapter)
              } else {
                // If not playing, we should probably start playback from this chapter?
                // PlayerViewModel might not have a direct method for "play book from chapter X".
                // For now, if we click, we toggle play if it's the current book, or we ignore/start book?
                // simpler: just call setChapter if it's prepared.
                if (isPlaybackReady) {
                  playerViewModel.setChapter(chapter)
                }
              }
            },
            isSelected = isPlayingChapter,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            maxDuration = maxDuration,
            isCached = isCached,
            canPlay = isCached || isOnline,
          )

          if (index < chapters.size - 1) {
            HorizontalDivider(
              thickness = 1.dp,
              modifier = Modifier.padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).alpha(0.1f),
            )
          }
        }

        item {
          Spacer(modifier = Modifier.height(32.dp)) // Bottom padding
        }
      }
    }
  }

  if (downloadsExpanded) {
    DownloadsComposable(
      libraryType = preferredLibrary?.type ?: LibraryType.UNKNOWN,
      hasCachedEpisodes = hasDownloadedChapters,
      isOnline = isOnline,
      cachingInProgress = cacheProgress.status is CacheStatus.Caching,
      onRequestedDownload = { option ->
        playingBook?.let {
          cachingModelView.cache(
            mediaItem = it,
            currentPosition = playerViewModel.totalPosition.value ?: 0.0,
            option = option,
          )
        }
      },
      onRequestedDrop = {
        playingBook?.let {
          scope.launch {
            cachingModelView.dropCache(it.id)
          }
        }
      },
      onRequestedStop = {
        playingBook?.let {
          scope.launch {
            cachingModelView.stopCaching(it)
          }
        }
      },
      onDismissRequest = { downloadsExpanded = false },
    )
  }
}

@Composable
fun DetailRow(
  icon: ImageVector,
  label: String,
  value: String,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(vertical = 6.dp),
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    Spacer(modifier = Modifier.width(12.dp))
    Text(text = "$label: ", style = typography.bodyMedium, color = colorScheme.onSurface.copy(alpha = 0.6f))
    Text(text = value, style = typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
fun ExpandableDescription(description: String) {
  var expanded by remember { androidx.compose.runtime.mutableStateOf(false) }
  var overflowDetected by remember { androidx.compose.runtime.mutableStateOf(false) }

  val html = description.replace("\n", "<br>")
  val spanned = remember(html) { HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY) }

  Column {
    Text(
      text = spanned.toString(),
      style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 24.sp),
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
      maxLines = if (expanded) Int.MAX_VALUE else 3,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.animateContentSize(),
      onTextLayout = { result ->
        if (!expanded && result.hasVisualOverflow) {
          overflowDetected = true
        }
      },
    )

    if (overflowDetected) {
      Text(
        text = if (expanded) stringResource(R.string.book_detail_see_less) else stringResource(R.string.book_detail_read_more),
        style =
          MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          ),
        modifier =
          Modifier
            .clickable { expanded = !expanded }
            .padding(top = 4.dp),
      )
    }
  }
}

@Composable
fun BookInfoTile(
  label: String,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        .padding(vertical = 12.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Top,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(4.dp))
    content()
  }
}
