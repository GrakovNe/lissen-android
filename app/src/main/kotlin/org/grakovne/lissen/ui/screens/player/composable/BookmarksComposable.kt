package org.grakovne.lissen.ui.screens.player.composable

import android.view.View
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.buildBookmarkTitle
import org.grakovne.lissen.common.withHaptic
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksComposable(
  playerViewModel: PlayerViewModel,
  onDismissRequest: () -> Unit,
) {
  val view: View = LocalView.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val createBookmarkFocusRequester = remember { FocusRequester() }

  val currentPlayingItem by playerViewModel.book.observeAsState()
  val currentPosition by playerViewModel.currentChapterPosition.observeAsState()
  val currentTrackIndex by playerViewModel.currentChapterIndex.observeAsState(0)
  val bookmarks by playerViewModel.bookmarks.observeAsState(emptyList())

  var isEditingCreateBookmark by remember { mutableStateOf(false) }
  var createBookmarkField by remember { mutableStateOf(TextFieldValue("")) }

  val currentChapterTitle =
    currentPlayingItem
      ?.chapters
      ?.getOrNull(currentTrackIndex)
      ?.title

  LaunchedEffect(isEditingCreateBookmark) {
    if (isEditingCreateBookmark) {
      createBookmarkFocusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  fun startEditing() {
    createBookmarkField = TextFieldValue(text = "", selection = TextRange(0))
    isEditingCreateBookmark = true
  }

  fun stopEditing() {
    isEditingCreateBookmark = false
    keyboardController?.hide()
    focusManager.clearFocus()
  }

  fun submitBookmark() {
    val title = createBookmarkField.text.trim()
    if (title.isNotEmpty()) {
      playerViewModel.createBookmark(title)
    } else {
      playerViewModel.createBookmark()
    }
    stopEditing()
  }

  ModalBottomSheet(
    containerColor = colorScheme.background,
    onDismissRequest = {
      stopEditing()
      onDismissRequest()
    },
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
        text = stringResource(R.string.bookmarks_title),
        style = typography.bodyLarge,
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        item {
          val chapterTitle = currentChapterTitle ?: return@item
          val position = currentPosition ?: return@item

          BookmarkRow(
            title = stringResource(R.string.bookmarks_create_bookmark),
            timeText =
              buildBookmarkTitle(
                currentChapterTitle = chapterTitle,
                currentChapterPosition = position,
              ),
            titleColor = colorScheme.primary,
            timeColor = colorScheme.onBackground.copy(alpha = 0.6f),
            editableTime = isEditingCreateBookmark,
            editableTimeValue = createBookmarkField,
            editableTimeFocusRequester = createBookmarkFocusRequester,
            onEditableTimeValueChange = { createBookmarkField = it },
            onEditableTimeSubmit = { submitBookmark() },
            onClick = { withHaptic(view = view) { startEditing() } },
            trailing = {
              IconButton(onClick = { withHaptic(view = view) { submitBookmark() } }) {
                Icon(
                  imageVector = Icons.Outlined.BookmarkAdd,
                  contentDescription = null,
                  tint = colorScheme.primary,
                )
              }
            },
          )

          if (bookmarks.isNotEmpty()) {
            HorizontalDivider()
          }
        }

        itemsIndexed(bookmarks) { index, item ->
          BookmarkRow(
            title = item.title,
            timeText = item.totalPosition.toInt().formatTime(true),
            titleColor = colorScheme.onBackground,
            timeColor = colorScheme.onBackground.copy(alpha = 0.6f),
            onClick = { playerViewModel.setTotalPosition(item.totalPosition) },
            trailing = {
              IconButton(
                onClick = { withHaptic(view) { playerViewModel.dropBookmark(item) } },
              ) {
                Icon(
                  imageVector = Icons.Outlined.DeleteOutline,
                  contentDescription = null,
                  tint = colorScheme.error,
                )
              }
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
  titleColor: Color,
  timeColor: Color,
  trailing: @Composable () -> Unit,
  onClick: (() -> Unit)? = null,
  editableTime: Boolean = false,
  editableTimeValue: TextFieldValue = TextFieldValue(""),
  editableTimeFocusRequester: FocusRequester? = null,
  onEditableTimeValueChange: (TextFieldValue) -> Unit = {},
  onEditableTimeSubmit: () -> Unit = {},
) {
  val view: View = LocalView.current
  val enabled = onClick != null
  val interactionSource = remember { MutableInteractionSource() }
  val indication = if (enabled) LocalIndication.current else null

  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clickable(
          enabled = enabled,
          interactionSource = interactionSource,
          indication = indication,
        ) { onClick?.let { withHaptic(view = view) { it.invoke() } } },
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

      if (editableTime) {
        BasicTextField(
          value = editableTimeValue,
          onValueChange = onEditableTimeValueChange,
          modifier =
            Modifier.then(
              if (editableTimeFocusRequester != null) {
                Modifier.focusRequester(editableTimeFocusRequester)
              } else {
                Modifier
              },
            ),
          textStyle = typography.bodySmall.copy(color = timeColor),
          singleLine = true,
          cursorBrush = SolidColor(timeColor),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { onEditableTimeSubmit() }),
          decorationBox = { innerTextField -> innerTextField() },
        )
      } else {
        Text(
          text = timeText,
          style = typography.bodySmall,
          color = timeColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    trailing()
  }
}
