package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.NumberItemDownloadOption
import org.grakovne.lissen.domain.RemainingItemsDownloadOption
import org.grakovne.lissen.ui.components.LissenModalBottomSheet
import org.grakovne.lissen.ui.screens.common.ChaptersCountStepper
import org.grakovne.lissen.ui.screens.common.makeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsComposable(
  isForceCache: Boolean,
  libraryType: LibraryType,
  hasCachedEpisodes: Boolean,
  cachingInProgress: Boolean,
  chaptersCount: Int,
  maxChaptersCount: Int,
  onChaptersCountChanged: (Int) -> Unit,
  onRequestedDownload: (DownloadOption) -> Unit,
  onRequestedStop: () -> Unit,
  onRequestedDrop: () -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current

  val maxCount = maxChaptersCount.coerceAtLeast(1)
  var count by rememberSaveable { mutableIntStateOf(chaptersCount.coerceIn(1, maxCount)) }

  val optionColor =
    when (isForceCache) {
      true -> colorScheme.onBackground.copy(alpha = 0.4f)
      false -> colorScheme.onBackground
    }

  LissenModalBottomSheet(
    containerColor = colorScheme.background,
    scrollable = false,
    onDismissRequest = onDismissRequest,
    content = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text =
            when (libraryType) {
              LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_book)
              LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_podcast)
              LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_unknown)
            },
          style = typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          item {
            DownloadOptionItem(
              text = CurrentItemDownloadOption.makeText(context, libraryType),
              color = optionColor,
              enabled = isForceCache.not(),
            ) {
              onRequestedDownload(CurrentItemDownloadOption)
              onDismissRequest()
            }
            HorizontalDivider()
          }

          item {
            ListItem(
              headlineContent = {
                Text(
                  text =
                    when (libraryType) {
                      LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_option_next_chapters_label)
                      LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_option_next_episodes_label)
                      LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_option_next_items_label)
                    },
                  style = typography.bodyMedium,
                  color = optionColor,
                )
              },
              trailingContent = {
                ChaptersCountStepper(
                  count = count,
                  maxCount = maxCount,
                  enabled = isForceCache.not(),
                  numberColor = optionColor,
                  onCountChanged = {
                    count = it
                    onChaptersCountChanged(it)
                  },
                )
              },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable(enabled = isForceCache.not()) {
                    onRequestedDownload(NumberItemDownloadOption(count.coerceAtMost(maxCount)))
                    onDismissRequest()
                  },
            )
            HorizontalDivider()
          }

          item {
            DownloadOptionItem(
              text = RemainingItemsDownloadOption.makeText(context, libraryType),
              color = optionColor,
              enabled = isForceCache.not(),
            ) {
              onRequestedDownload(RemainingItemsDownloadOption)
              onDismissRequest()
            }
            HorizontalDivider()
          }

          item {
            DownloadOptionItem(
              text = AllItemsDownloadOption.makeText(context, libraryType),
              color = optionColor,
              enabled = isForceCache.not(),
            ) {
              onRequestedDownload(AllItemsDownloadOption)
              onDismissRequest()
            }
          }

          if (cachingInProgress) {
            item {
              HorizontalDivider()

              DownloadOptionItem(
                text = stringResource(R.string.downloads_menu_download_option_stop_downloads),
                color = colorScheme.error,
                enabled = true,
              ) {
                onRequestedStop()
                onDismissRequest()
              }
            }
          }

          if (hasCachedEpisodes) {
            item {
              HorizontalDivider()

              DownloadOptionItem(
                text =
                  when (libraryType) {
                    LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_option_clear_chapters)
                    LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_option_clear_episodes)
                    LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_option_clear_items)
                  },
                color = colorScheme.error,
                enabled = true,
              ) {
                onRequestedDrop()
                onDismissRequest()
              }
            }
          }
        }
      }
    },
  )
}

@Composable
private fun DownloadOptionItem(
  text: String,
  color: Color,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  ListItem(
    headlineContent = {
      Row {
        Text(
          text = text,
          style = typography.bodyMedium,
          color = color,
        )
      }
    },
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled, onClick = onClick),
  )
}
