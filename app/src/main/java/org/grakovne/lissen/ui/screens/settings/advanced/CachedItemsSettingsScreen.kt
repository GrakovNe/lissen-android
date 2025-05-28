package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.grakovne.lissen.R

data class Book(
  val id: String,
  val title: String,
  val author: String?,
  val chapters: List<Chapter>,
)

data class Chapter(
  val id: String,
  val title: String,
  val duration: String = "12:00",
  val sizeMb: String = "5.3 МБ",
)

private val sampleBooks =
  listOf(
    Book(
      "1",
      "The Ocean at the End of the Lane",
      "Neil Gaiman",
      listOf(
        Chapter("1", "Chapter One", "10:35", "6.1 МБ"),
        Chapter("2", "Chapter Two", "12:45", "7.2 МБ"),
      ),
    ),
    Book(
      "2",
      "Dune",
      "Frank Herbert",
      listOf(
        Chapter("1", "Arrakis", "15:20", "8.4 МБ"),
        Chapter("2", "Muad'Dib", "13:10", "7.9 МБ"),
      ),
    ),
    Book(
      "3",
      "1984",
      "George Orwell",
      listOf(
        Chapter("1", "The Principles of Newspeak", "09:40", "5.2 МБ"),
        Chapter("2", "Big Brother Is Watching You", "11:05", "6.5 МБ"),
      ),
    ),
    Book(
      "4",
      "Brave New World",
      "Aldous Huxley",
      listOf(
        Chapter("1", "The World State", "14:00", "8.1 МБ"),
        Chapter("2", "Conditioning", "13:45", "7.6 МБ"),
      ),
    ),
    Book(
      "5",
      "The Martian",
      "Andy Weir",
      listOf(
        Chapter("1", "I'm Pretty Much Fucked", "12:20", "6.8 МБ"),
        Chapter("2", "Problem Solving", "13:15", "7.1 МБ"),
      ),
    ),
  )

class BooksViewModel : ViewModel() {
  private val _books = MutableStateFlow(sampleBooks)
  val books: StateFlow<List<Book>> = _books.asStateFlow()

  fun deleteBook(bookId: String) {
    _books.value = _books.value.filterNot { it.id == bookId }
  }

  fun deleteChapter(
    bookId: String,
    chapterId: String,
  ) {
    _books.value =
      _books.value.mapNotNull { book ->
        if (book.id == bookId) {
          val updatedChapters = book.chapters.filterNot { it.id == chapterId }
          if (updatedChapters.isNotEmpty()) book.copy(chapters = updatedChapters) else null
        } else {
          book
        }
      }
  }
}

private val thumbnailSize = 64.dp
private val spacing = 16.dp
private val chapterIndent = thumbnailSize + spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedItemsSettingsScreen(viewModel: BooksViewModel = viewModel()) {
  val books by viewModel.books.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(R.string.content_downloads_list_settings_screen_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
          )
        },
        navigationIcon = {
          IconButton(onClick = { /* back */ }) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      contentPadding =
        PaddingValues(
          top = innerPadding.calculateTopPadding(),
          bottom = innerPadding.calculateBottomPadding(),
        ),
      modifier = Modifier.fillMaxSize(),
    ) {
      items(items = books) { book ->
        CachedItemComposable(book)
      }
    }
  }
}

@Composable
private fun CachedItemComposable(
  book: Book,
  viewModel: BooksViewModel = viewModel(),
) {
  var expanded by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable { expanded = !expanded }
        .padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Column {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier =
            Modifier
              .size(thumbnailSize)
              .clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(Modifier.width(spacing))

        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = book.title,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onBackground,
                ),
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.width(4.dp))

            Icon(
              imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.onBackground,
            )
          }
          book.author?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = it,
              style =
                MaterialTheme.typography.bodyMedium.copy(
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(Modifier.width(spacing))

        IconButton(onClick = { viewModel.deleteBook(book.id) }) {
          Icon(
            imageVector = Icons.Outlined.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      if (expanded) {
        CachedItemChapterComposable(book)
      }
    }
  }
}

@Composable
private fun CachedItemChapterComposable(
  book: Book,
  viewModel: BooksViewModel = viewModel(),
) {
  Spacer(modifier = Modifier.height(spacing))
  book.chapters.forEach { chapter ->
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(start = chapterIndent, end = spacing, top = spacing, bottom = spacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(text = chapter.title, style = MaterialTheme.typography.bodyMedium)
        Text(
          text = "${chapter.duration} • ${chapter.sizeMb}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
      }
      IconButton(onClick = {
        viewModel.deleteChapter(book.id, chapter.id)
      }) {
        Icon(
          imageVector = Icons.Outlined.Delete,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
