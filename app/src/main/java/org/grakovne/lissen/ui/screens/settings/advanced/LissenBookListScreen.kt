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
data class Chapter(val id: String, val title: String)

// Sample data
private val sampleBooks = listOf(
  Book("1", "Океан в конце дороги", "Нил Гейман", listOf(
    Chapter("1", "Глава 1"), Chapter("2", "Глава 2")
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
        if (updatedChapters.isNotEmpty()) {
          book.copy(chapters = updatedChapters)
        } else {
          null
        }
      } else {
        book
      }
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
              Text(
                text = chapter.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium
              )
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
