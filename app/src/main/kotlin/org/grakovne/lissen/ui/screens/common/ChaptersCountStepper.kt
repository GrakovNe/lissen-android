package org.grakovne.lissen.ui.screens.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.grakovne.lissen.R
import org.grakovne.lissen.common.withHaptic

@Composable
fun ChaptersCountStepper(
  count: Int,
  onCountChanged: (Int) -> Unit,
  modifier: Modifier = Modifier,
  minCount: Int = 1,
  maxCount: Int = Int.MAX_VALUE,
  enabled: Boolean = true,
  numberColor: Color = Color.Unspecified,
) {
  val view = LocalView.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }

  val stepperColors = IconButtonDefaults.iconButtonColors(contentColor = colorScheme.primary)
  val resolvedNumberColor = if (numberColor == Color.Unspecified) LocalContentColor.current else numberColor

  var isEditing by remember { mutableStateOf(false) }
  var hasFocused by remember { mutableStateOf(false) }
  var field by remember { mutableStateOf(TextFieldValue("")) }

  fun startEditing() {
    val text = count.toString()
    field = TextFieldValue(text = text, selection = TextRange(0, text.length))
    hasFocused = false
    isEditing = true
  }

  fun commit() {
    if (!isEditing) return
    // Allow values above maxCount so the user sees exactly what they typed; the actual
    // download is clamped to the real maximum by the caller.
    field.text.toIntOrNull()?.let { onCountChanged(it.coerceAtLeast(minCount)) }
    isEditing = false
    keyboardController?.hide()
    focusManager.clearFocus()
  }

  LaunchedEffect(isEditing) {
    if (isEditing) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.End,
  ) {
    IconButton(
      modifier = Modifier.size(32.dp),
      colors = stepperColors,
      enabled = enabled && count > minCount,
      onClick = {
        withHaptic(view) {
          val next = if (count > maxCount) maxCount else (count - 1).coerceAtLeast(minCount)
          onCountChanged(next)
        }
      },
    ) {
      Icon(
        imageVector = Icons.Rounded.Remove,
        contentDescription = stringResource(R.string.downloads_menu_download_option_decrease),
        modifier = Modifier.size(18.dp),
      )
    }

    if (isEditing) {
      BasicTextField(
        value = field,
        onValueChange = { field = it.copy(text = it.text.filter(Char::isDigit)) },
        modifier =
          Modifier
            .width(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
              when {
                it.isFocused -> hasFocused = true
                hasFocused -> commit()
              }
            },
        textStyle = typography.titleMedium.copy(color = resolvedNumberColor, textAlign = TextAlign.Center),
        singleLine = true,
        cursorBrush = SolidColor(colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        decorationBox = { innerTextField ->
          Box(contentAlignment = Alignment.Center) { innerTextField() }
        },
      )
    } else {
      Text(
        text = count.toString(),
        style = typography.titleMedium,
        color = resolvedNumberColor,
        textAlign = TextAlign.Center,
        modifier =
          Modifier
            .width(48.dp)
            .clickable(enabled = enabled) { withHaptic(view) { startEditing() } },
      )
    }

    IconButton(
      modifier = Modifier.size(32.dp),
      colors = stepperColors,
      enabled = enabled && count < maxCount,
      onClick = {
        withHaptic(view) {
          onCountChanged((count + 1).coerceIn(minCount, maxCount))
        }
      },
    ) {
      Icon(
        imageVector = Icons.Rounded.Add,
        contentDescription = stringResource(R.string.downloads_menu_download_option_increase),
        modifier = Modifier.size(18.dp),
      )
    }
  }
}
