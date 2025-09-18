package org.grakovne.lissen.ui.screens.player.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import org.grakovne.lissen.R
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.LibraryType
import org.grakovne.lissen.ui.extensions.formatTime
import org.grakovne.lissen.ui.screens.player.InfoRow
import org.grakovne.lissen.viewmodel.PlayerViewModel
import org.grakovne.lissen.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailComposable(
  playingBook: DetailedItem?,
  onDismissRequest: () -> Unit,
  playingViewModel: PlayerViewModel,
  settingsViewModel: SettingsViewModel,
) {
  val totalPosition by playingViewModel.totalPosition.observeAsState(0.0)
  val preferredLibrary by settingsViewModel.preferredLibrary.observeAsState()

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    containerColor = colorScheme.surface,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(vertical = 16.dp, horizontal = 4.dp),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
      ) {
        val seriesValue =
          playingBook
            ?.series
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { series ->
              buildString {
                append(series.name)
                series.serialNumber
                  ?.takeIf(String::isNotBlank)
                  ?.let { append(" #$it") }
              }
            }

        playingBook
          ?.title
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            Text(
              text = it,
              style =
                typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                ),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              color = colorScheme.onSurface,
              modifier =
                when (seriesValue?.isNotBlank()) {
                  true -> Modifier
                  else -> Modifier.padding(bottom = 8.dp)
                },
            )
          }

        seriesValue
          ?.let {
            Text(
              text = it,
              style = typography.titleSmall,
              color = colorScheme.onBackground.copy(alpha = 0.6f),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }

        playingBook
          ?.author
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            InfoRow(
              icon = Icons.Outlined.Person,
              label = stringResource(R.string.playing_item_details_author),
              textValue = it,
            )
          }

        playingBook
          ?.narrator
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            InfoRow(
              icon = Icons.Outlined.MicNone,
              label = stringResource(R.string.playing_item_details_narrator),
              textValue = it,
            )
          }

        if (preferredLibrary?.type == LibraryType.LIBRARY) {
          playingBook
            ?.chapters
            ?.sumOf { it.duration }
            ?.let {
              InfoRow(
                icon = Icons.Filled.AvTimer,
                label = stringResource(R.string.playing_item_details_duration),
                textValue = it.toInt().formatTime(),
              )

              InfoRow(
                icon = Icons.Filled.HourglassEmpty,
                label = stringResource(R.string.playing_item_details_time_remaining),
                textValue = (it - totalPosition).toInt().formatTime(),
              )
            }
        }

        playingBook
          ?.publisher
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            InfoRow(
              icon = Icons.Outlined.Business,
              label = stringResource(R.string.playing_item_details_publisher),
              textValue = it,
            )
          }

        playingBook
          ?.year
          ?.takeIf { it.isNotEmpty() }
          ?.let {
            InfoRow(
              icon = Icons.Outlined.CalendarMonth,
              label = stringResource(R.string.playing_item_details_year),
              textValue = it,
            )
          }
      }

      playingBook
        ?.abstract
        ?.takeIf { it.isNotEmpty() }
        ?.let {
          HorizontalDivider(
            modifier =
              Modifier
                .padding(vertical = 16.dp, horizontal = 16.dp)
                .alpha(0.2f),
          )

          val html: String = playingBook.abstract?.replace("\n", "<br>").orEmpty()
          val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)

          Text(
            text = spanned.toString(),
            style = typography.bodyMedium.copy(lineHeight = 22.sp),
            color = colorScheme.onSurface,
            textAlign = TextAlign.Justify,
            modifier = Modifier.padding(horizontal = 16.dp),
          )
        }
    }
  }
}
