package org.grakovne.lissen.ui.screens.settings.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Data models
data class Book(val id: String, val title: String, val author: String?, val chapters: List<Chapter>)
data class Chapter(val id: String, val title: String, val duration: String = "12:00", val sizeMb: String = "5.3 МБ")

// Sample data
private val sampleBooks = listOf(
  Book("1", "The Ocean at the End of the Lane", "Neil Gaiman", listOf(
    Chapter("1", "Chapter One", "10:35", "6.1 МБ"),
    Chapter("2", "Chapter Two", "12:45", "7.2 МБ")
  )),
  Book("2", "Dune", "Frank Herbert", listOf(
    Chapter("1", "Arrakis", "15:20", "8.4 МБ"),
    Chapter("2", "Muad'Dib", "13:10", "7.9 МБ")
  )),
  Book("3", "1984", "George Orwell", listOf(
    Chapter("1", "The Principles of Newspeak", "09:40", "5.2 МБ"),
    Chapter("2", "Big Brother Is Watching You", "11:05", "6.5 МБ")
  )),
  Book("4", "Brave New World", "Aldous Huxley", listOf(
    Chapter("1", "The World State", "14:00", "8.1 МБ"),
    Chapter("2", "Conditioning", "13:45", "7.6 МБ")
  )),
  Book("5", "The Martian", "Andy Weir", listOf(
    Chapter("1", "I'm Pretty Much Fucked", "12:20", "6.8 МБ"),
    Chapter("2", "Problem Solving", "13:15", "7.1 МБ")
  ))
)

class BooksViewModel : ViewModel() {
  private val _books = MutableStateFlow(sampleBooks)
  val books: StateFlow<List<Book>> = _books.asStateFlow()

  fun deleteBook(bookId: String) {
    _books.value = _books.value.filterNot { it.id == bookId }
  }

  fun deleteChapter(bookId: String, chapterId: String) {
    _books.value = _books.value.mapNotNull { book ->
      if (book.id == bookId) {
        val updatedChapters = book.chapters.filterNot { it.id == chapterId }
        if (updatedChapters.isNotEmpty()) book.copy(chapters = updatedChapters) else null
      } else book
    }
  }
}

@Composable
fun BooksScreen(
  books: List<Book>,
  onDeleteBook: (Book) -> Unit,
  onDeleteChapter: (Book, Chapter) -> Unit
) {
  LazyColumn(modifier = Modifier.fillMaxSize()) {
    items(books) { book ->
      var expanded by remember { mutableStateOf(false) }
      val textColor = MaterialTheme.colorScheme.onBackground

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { expanded = !expanded }
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(56.dp)
              .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
          )

          Spacer(Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor
              )
              Spacer(Modifier.width(4.dp))
              Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                modifier = Modifier.size(18.dp),
                tint = textColor
              )
            }
            if (!book.author.isNullOrBlank()) {
              Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
              )
            }
          }

          IconButton(onClick = { onDeleteBook(book) }) {
            Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = "Удалить книгу"
            )
          }
        }

        if (expanded) {
          Spacer(modifier = Modifier.height(4.dp))
          book.chapters.forEach { chapter ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 68.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = chapter.title,
                  style = MaterialTheme.typography.bodyMedium
                )
                Text(
                  text = "${chapter.duration} • ${chapter.sizeMb}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
              }
              IconButton(onClick = { onDeleteChapter(book, chapter) }) {
                Icon(
                  imageVector = Icons.Outlined.Delete,
                  contentDescription = "Удалить главу"
                )
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LissenBookListScreen(viewModel: BooksViewModel = viewModel()) {
  val books by viewModel.books.collectAsState()

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(title = { Text("Аудиокниги") })
    },
    bottomBar = {
      Surface(
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
      ) {
        val totalSize = books.sumOf { book ->
          book.chapters.sumOf {
            it.sizeMb.replace(" МБ", "").replace(",", ".").toDoubleOrNull() ?: 0.0
          }
        }
        val totalDurationMin = books.flatMap { it.chapters }
          .mapNotNull { ch ->
            ch.duration.split(":").mapNotNull { it.toIntOrNull() }.let {
              if (it.size == 2) it[0] * 60 + it[1] else null
            }
          }.sum() / 60

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Text(
            text = "Хранилище: %.1f ГБ".format(totalSize / 1024),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Общая длительность: $totalDurationMin мин",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding)) {
      BooksScreen(
        books = books,
        onDeleteBook = { viewModel.deleteBook(it.id) },
        onDeleteChapter = { book, chapter ->
          viewModel.deleteChapter(book.id, chapter.id)
        }
      )
    }
  }
}
