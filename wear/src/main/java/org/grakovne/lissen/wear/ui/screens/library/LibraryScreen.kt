package org.grakovne.lissen.wear.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.Section
import com.google.android.horologist.composables.SectionedList
import com.google.android.horologist.compose.layout.AppScaffold
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.ScreenScaffold
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import org.grakovne.lissen.lib.domain.RecentBook
import org.grakovne.lissen.wear.presentation.theme.LissenTheme

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun LibraryScreen(
  onBookSelected: () -> Unit = {}
//  viewModelodel: LibraryScreenViewModel = hiltViewModel<>()
) {
  val contentPadding = rememberResponsiveColumnPadding(
    first = ColumnItemType.ButtonRow,
    last = ColumnItemType.EdgeButtonPadding,
  )
  val columnState = rememberResponsiveColumnState(
    contentPadding = { contentPadding }
  )
  val transformationSpec = rememberTransformationSpec()

  val recentBooks: List<RecentBook> = List(4, init = { i ->
    RecentBook(
      "$i", "Book $i", "A story of something", "Writy McWriteface", 25,
      123
    )
  })

  ScreenScaffold(
    scrollState = columnState,
//    contentPadding = contentPadding
  ) {

    SectionedList(
      columnState = columnState
    ) {
//      section {
//        loaded {
//          Row(
//            modifier = Modifier.fillMaxWidth(.8f),
//            horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.CenterHorizontally)
//          ) {
//            val  modifier = Modifier.touchTargetAwareSize(IconButtonDefaults.ExtraSmallButtonSize)
//
//            FilledTonalIconButton(
//              modifier = modifier,
//              onClick = {},
//            ) {
//              Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
//            }
//
//            FilledTonalIconButton(
//              modifier = modifier,
//              onClick = {},
//            ) {
//              Icon(imageVector = Icons.Default.FilterList, contentDescription = "Filter")
//            }
//          }
//        }
//      }

      section(state = Section.State.Loaded(list = recentBooks)) {
        header {
          Text("Continue Listening")
        }
        loaded { book ->
          BookListItem(
            book = book,
            onClick = {
              onBookSelected()
            }
          )
        }
      }

      section {
        loaded {
          EdgeButton(
            modifier = Modifier.padding(top = 25.dp),
            buttonSize = EdgeButtonSize.ExtraSmall,
            onClick = {}
          ) { Text("Manage") }
        }
      }
    }
  }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun BookListItem(
  modifier: Modifier = Modifier,
  book: RecentBook,
  onClick: () -> Unit
) {
  FilledTonalButton(
    modifier = modifier.fillMaxWidth(),
    onClick = onClick,
    icon = {
    }
  ) {
    Column {
      Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(book.author ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
    }
  }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
  LissenTheme {
    AppScaffold {
      LibraryScreen()
    }
  }
}
