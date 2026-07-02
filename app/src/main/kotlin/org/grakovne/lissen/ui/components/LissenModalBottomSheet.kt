package org.grakovne.lissen.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LissenModalBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  containerColor: Color = colorScheme.background,
  scrollable: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier,
    containerColor = containerColor,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    when (scrollable) {
      true -> {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          content = content,
        )
      }

      false -> {
        content()
      }
    }
  }
}
