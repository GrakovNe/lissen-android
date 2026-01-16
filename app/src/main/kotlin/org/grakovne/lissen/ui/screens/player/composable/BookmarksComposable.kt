package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksComposable(
  playerViewModel: PlayerViewModel,
  onDismissRequest: () -> Unit,
) {
  val bookmarks by playerViewModel.bookmarks.observeAsState(emptyList())

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = onDismissRequest,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
          .padding(start = 16.dp, end = 4.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "Закладки",
        style = typography.bodyLarge,
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        item {
          BookmarkRow(
            title = "Создать закладку",
            timeText = "Текущая позиция",
            titleColor = colorScheme.primary,
            timeColor = colorScheme.onBackground.copy(alpha = 0.6f),
            trailing = {
              IconButton(
                onClick = {
                  // onDismissRequest()
                },
              ) {
                Icon(
                  imageVector = Icons.Outlined.BookmarkAdd,
                  contentDescription = null,
                  tint = colorScheme.primary,
                )
              }
            },
            onClick = {
              // onDismissRequest()
            },
          )
          HorizontalDivider()
        }

        itemsIndexed(bookmarks) { index, item ->
          BookmarkRow(
            title = item.title,
            timeText = item.totalPosition.toInt().formatTime(true),
            titleColor = colorScheme.onBackground,
            timeColor = colorScheme.onBackground.copy(alpha = 0.6f),
            trailing = {
              IconButton(
                onClick = {
                  //
                },
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = null,
                  tint = colorScheme.error,
                )
              }
            },
            onClick = {
              //
            },
          )

          if (index < bookmarks.size - 1) {
            HorizontalDivider()
          }
        }
      }
    }
  }
}

@Composable
private fun BookmarkRow(
  title: String,
  timeText: String,
  titleColor: androidx.compose.ui.graphics.Color,
  timeColor: androidx.compose.ui.graphics.Color,
  trailing: @Composable () -> Unit,
  onClick: () -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(
      modifier =
        Modifier
          .weight(1f)
          .padding(start = 16.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = typography.bodyMedium,
        color = titleColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = timeText,
        style = typography.bodySmall,
        color = timeColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    trailing()
  }
}
