package org.grakovne.lissen.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.BookSeries
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestionSource
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MediaProgress
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.selectFinishedSuggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FinishedSuggestionAcceptanceTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var mediaProvider: LissenMediaProvider

  private lateinit var libraryId: String
  private lateinit var books: List<Book>

  @Before
  fun login() {
    hiltRule.inject()

    runBlocking {
      mediaProvider.authorize(E2E_HOST, E2E_USERNAME, E2E_PASSWORD)

      libraryId =
        mediaProvider
          .fetchLibraries()
          .fold(onSuccess = { libraries -> libraries.first().id }, onFailure = { error("no libraries on $E2E_HOST") })

      books = fetchAllBooks()
    }

    assertTrue("demo library must contain books", books.size >= 5)
  }

  @Test
  fun notStartedFilterSucceedsOnRealServer() {
    runBlocking {
      val page =
        mediaProvider
          .fetchBooks(libraryId = libraryId, pageSize = 100, pageNumber = 0, extraFilter = "progress" to "not-started")
          .fold(onSuccess = { it }, onFailure = { error("not-started filter failed") })

      val notFinished =
        mediaProvider
          .fetchBooks(libraryId = libraryId, pageSize = 100, pageNumber = 0, extraFilter = "progress" to "not-finished")
          .fold(onSuccess = { it.items.map { book -> book.id }.toSet() }, onFailure = { error("not-finished filter failed") })

      assertTrue(notFinished.containsAll(page.items.map { it.id }))
    }
  }

  @Test
  fun authorFilterSucceedsOnRealServer() {
    runBlocking {
      val first =
        mediaProvider
          .fetchBook(books.first().id)
          .fold(onSuccess = { it }, onFailure = { error("book fetch failed") })

      val authorId = first.authors.firstOrNull()?.id ?: error("book has no author id")

      val page =
        mediaProvider
          .fetchBooks(libraryId = libraryId, pageSize = 100, pageNumber = 0, extraFilter = "authors" to authorId)
          .fold(onSuccess = { it }, onFailure = { error("author filter failed") })

      assertTrue(page.items.any { it.id == first.id })
    }
  }

  @Test
  fun threeNextBooksInSeriesAreSuggested() {
    val finished = books.first()
    val following = books.filter { it.id != finished.id }.take(3)
    val seriesBooks = listOf(finished) + following

    val suggestion =
      selectFinishedSuggestion(finishedItem(finished, seriesName = "Acceptance Series"), seriesBooks, emptyList(), emptyList())

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(following.map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun twoNextBooksInSeriesAreSuggested() {
    val finished = books.first()
    val following = books.filter { it.id != finished.id }.take(2)

    val suggestion =
      selectFinishedSuggestion(
        finishedItem(finished, seriesName = "Acceptance Series"),
        listOf(finished) + following,
        emptyList(),
        emptyList(),
      )

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(following.map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun singleNextBookInSeriesIsSuggested() {
    val finished = books.first()
    val following = books.filter { it.id != finished.id }.take(1)

    val suggestion =
      selectFinishedSuggestion(
        finishedItem(finished, seriesName = "Acceptance Series"),
        listOf(finished) + following,
        emptyList(),
        emptyList(),
      )

    assertEquals(FinishedSuggestionSource.SERIES, suggestion?.source)
    assertEquals(following.map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun threeAuthorBooksAreSuggestedWhenSeriesIsExhausted() {
    val author = "Acceptance Author"
    val authored = books.take(6).map { it.copy(author = author) }
    val finished = authored.first()
    val authorCandidates = authored.subList(3, 6)

    val suggestion =
      selectFinishedSuggestion(
        finishedItem(finished, seriesName = "Acceptance Series"),
        seriesBooks = listOf(finished),
        authorBooks = authorCandidates,
        libraryBooks = books.drop(6),
      )

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(authorCandidates.map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun twoAuthorBooksAreSuggested() {
    val author = "Acceptance Author"
    val authored = books.take(3).map { it.copy(author = author) }
    val finished = authored.first()

    val suggestion = selectFinishedSuggestion(finishedItem(finished), emptyList(), authored.drop(1), emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(authored.drop(1).map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun singleAuthorBookIsSuggested() {
    val author = "Acceptance Author"
    val authored = books.take(2).map { it.copy(author = author) }
    val finished = authored.first()

    val suggestion = selectFinishedSuggestion(finishedItem(finished), emptyList(), authored.drop(1), emptyList())

    assertEquals(FinishedSuggestionSource.AUTHOR, suggestion?.source)
    assertEquals(authored.drop(1).map { it.id }, suggestion?.candidates?.map { it.id })
  }

  @Test
  fun threeLibraryBooksAreSuggestedWhenNothingMatches() {
    val finished = books.first().copy(author = "Unique Acceptance Author")
    val pool = books.filter { it.id != finished.id }.map { it.copy(author = "Someone Else") }

    val suggestion =
      selectFinishedSuggestion(finishedItem(finished), emptyList(), emptyList(), pool)

    assertEquals(FinishedSuggestionSource.LIBRARY, suggestion?.source)
    assertEquals(3, suggestion?.candidates?.size)
    assertTrue(pool.map { it.id }.toSet().containsAll(suggestion?.candidates?.map { it.id } ?: emptyList()))
  }

  @Test
  fun noSuggestionWhenLibraryHasOnlyTheFinishedBook() {
    val finished = books.first()

    val suggestion = selectFinishedSuggestion(finishedItem(finished), emptyList(), emptyList(), listOf(finished))

    assertNull(suggestion)
  }

  private suspend fun fetchAllBooks(): List<Book> {
    val collected = mutableListOf<Book>()
    var done = false

    while (!done) {
      done =
        mediaProvider
          .fetchBooks(libraryId = libraryId, pageSize = 100, pageNumber = collected.size / 100)
          .fold(
            onSuccess = { page ->
              collected += page.items
              page.items.isEmpty() || collected.size >= page.totalItems
            },
            onFailure = { true },
          )
    }

    return collected
  }

  private fun finishedItem(
    book: Book,
    seriesName: String? = null,
  ): DetailedItem =
    DetailedItem(
      id = book.id,
      title = book.title,
      subtitle = book.subtitle,
      author = book.author,
      narrator = null,
      publisher = null,
      series = seriesName?.let { listOf(BookSeries(serialNumber = "1", name = it, id = "acceptance-series")) }.orEmpty(),
      year = null,
      abstract = null,
      files = emptyList(),
      chapters =
        listOf(
          PlayingChapter(
            available = true,
            podcastEpisodeState = null,
            duration = 61_200.0,
            start = 0.0,
            end = 61_200.0,
            title = "The Whole Book",
            id = "${book.id}-chapter",
          ),
        ),
      progress = MediaProgress(currentTime = 61_200.0, isFinished = true, lastUpdate = 0L),
      libraryId = libraryId,
      libraryType = LibraryType.LIBRARY,
      localProvided = false,
      createdAt = 0L,
      updatedAt = 0L,
    )
}
