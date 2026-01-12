package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.lib.domain.TimerOption
import org.grakovne.lissen.ui.components.slider.SleepTimerSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerComposable(
  currentOption: TimerOption?,
  libraryType: LibraryType,
  onOptionSelected: (TimerOption?) -> Unit,
  onDismissRequest: () -> Unit,
) {
  val context = LocalContext.current

  ModalBottomSheet(
    containerColor = colorScheme.background,
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
          text = stringResource(R.string.timer_title),
          style = typography.bodyLarge,
        )

        SleepTimerSlider(
          libraryType = libraryType,
          context = context,
          option = currentOption,
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp),
          onUpdate = { onOptionSelected(it) },
        )
      }
    },
  )
}
