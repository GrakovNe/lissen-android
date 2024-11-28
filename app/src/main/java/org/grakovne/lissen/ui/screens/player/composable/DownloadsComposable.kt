package org.grakovne.lissen.ui.screens.player.composable

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.channel.common.LibraryType
import org.grakovne.lissen.domain.AllItemsDownloadOption
import org.grakovne.lissen.domain.CurrentItemDownloadOption
import org.grakovne.lissen.domain.DownloadOption
import org.grakovne.lissen.domain.NumberItemDownloadOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsComposable(
    libraryType: LibraryType,
    hasCachedEpisodes: Boolean,
    onRequestedDownload: (DownloadOption) -> Unit,
    onRequestedDrop: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        containerColor = colorScheme.background,
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = when (libraryType) {
                        LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_book)
                        LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_podcast)
                        LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_unknown)
                    },
                    style = typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    itemsIndexed(DownloadOptions) { index, item ->
                        ListItem(
                            headlineContent = {
                                Row {
                                    Text(
                                        text = item.makeText(context, libraryType),
                                        style = typography.bodyMedium,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onRequestedDownload(item)
                                    onDismissRequest()
                                },
                        )
                        if (index < DownloadOptions.size - 1) {
                            HorizontalDivider()
                        }
                    }

                    if (hasCachedEpisodes) {
                        item {
                            HorizontalDivider()

                            ListItem(
                                headlineContent = {
                                    Row {
                                        Text(
                                            text = when (libraryType) {
                                                LibraryType.LIBRARY -> stringResource(R.string.downloads_menu_download_option_clear_chapters)
                                                LibraryType.PODCAST -> stringResource(R.string.downloads_menu_download_option_clear_episodes)
                                                LibraryType.UNKNOWN -> stringResource(R.string.downloads_menu_download_option_clear_items)
                                            },
                                            color = colorScheme.error,
                                            style = typography.bodyMedium,
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onRequestedDrop()
                                        onDismissRequest()
                                    },
                            )
                        }
                    }
                }
            }
        },
    )
}

private val DownloadOptions = listOf(
    CurrentItemDownloadOption,
    NumberItemDownloadOption(5),
    NumberItemDownloadOption(10),
    NumberItemDownloadOption(20),
    AllItemsDownloadOption,
)

fun DownloadOption.makeText(
    context: Context,
    libraryType: LibraryType,
): String = when (this) {
    CurrentItemDownloadOption -> {
        when (libraryType) {
            LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_current_chapter)
            LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_current_episode)
            LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_current_item)
        }
    }

    AllItemsDownloadOption -> {
        when (libraryType) {
            LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_entire_book)
            LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_entire_podcast)
            LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_entire_item)
        }
    }

    is NumberItemDownloadOption -> {
        when (libraryType) {
            LibraryType.LIBRARY -> context.getString(R.string.downloads_menu_download_option_next_chapters, itemsNumber)
            LibraryType.PODCAST -> context.getString(R.string.downloads_menu_download_option_next_episodes, itemsNumber)
            LibraryType.UNKNOWN -> context.getString(R.string.downloads_menu_download_option_next_items, itemsNumber)
        }
    }
}
