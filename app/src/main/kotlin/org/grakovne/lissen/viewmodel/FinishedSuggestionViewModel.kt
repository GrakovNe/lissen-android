package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.LibraryFilters
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.sortedBySeriesPosition
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestion
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.MAX_FINISHED_CANDIDATES
import org.grakovne.lissen.domain.primaryAuthor
import org.grakovne.lissen.domain.selectFinishedSuggestion
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.playback.MediaRepository
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class FinishedSuggestionViewModel
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val mediaChannel: LissenMediaProvider,
    private val libraryPreferences: LibraryPreferences,
  ) : ViewModel() {
    val finishedBook: StateFlow<DetailedItem?> = mediaRepository.finishedBook

    private val _suggestion = MutableStateFlow<FinishedSuggestion?>(null)
    val suggestion: StateFlow<FinishedSuggestion?> = _suggestion.asStateFlow()

    private var loading: Job? = null

    fun load(bookId: String) {
      loading?.cancel()
      _suggestion.value = null

      val book = finishedBook.value?.takeIf { it.id == bookId } ?: return
      val libraryId = book.libraryId ?: return
      val libraryType = book.libraryType ?: libraryPreferences.getPreferredLibraryType()

      if (libraryType != LibraryType.LIBRARY) {
        return
      }

      loading =
        viewModelScope.launch {
          _suggestion.value = resolveSuggestion(libraryId, libraryType, book)
        }
    }

    fun dismiss() {
      Timber.d("User action: dismiss finished suggestion")
      loading?.cancel()
      _suggestion.value = null
      mediaRepository.clearFinishedBook()
    }

    private suspend fun resolveSuggestion(
      libraryId: String,
      libraryType: LibraryType,
      book: DetailedItem,
    ): FinishedSuggestion? {
      book.series
        .firstOrNull { it.name.isNotBlank() }
        ?.let { series -> series.id?.let { it to series.name } }
        ?.let { (seriesId, seriesName) ->
          val seriesBooks =
            mediaChannel
              .fetchSeriesItems(libraryId = libraryId, seriesId = seriesId, libraryType = libraryType, includeFinished = true)
              .fold(onSuccess = { it.sortedBySeriesPosition(seriesName) }, onFailure = { emptyList() })

          selectFinishedSuggestion(book, seriesBooks, emptyList(), emptyList())?.let { return it }
        }

      book
        .primaryAuthor()
        ?.let { author ->
          val authorBooks = fetchCandidates(libraryId, libraryType, book, LibraryFilters.AUTHORS to author.id)

          selectFinishedSuggestion(book, emptyList(), authorBooks, emptyList())?.let { return it }
        }

      val libraryBooks = fetchCandidates(libraryId, libraryType, book, LibraryFilters.PROGRESS to LibraryFilters.NOT_STARTED)

      return selectFinishedSuggestion(book, emptyList(), emptyList(), libraryBooks)
    }

    private suspend fun fetchCandidates(
      libraryId: String,
      libraryType: LibraryType,
      finished: DetailedItem,
      filter: Pair<String, String>,
    ): List<Book> {
      val collected = mutableListOf<Book>()
      var pageNumber = 0

      while (collected.count { it.id != finished.id } < MAX_FINISHED_CANDIDATES) {
        when (
          val result =
            mediaChannel.fetchBooks(
              libraryId = libraryId,
              pageSize = MAX_FINISHED_CANDIDATES,
              pageNumber = pageNumber,
              extraFilter = filter,
              libraryType = libraryType,
            )
        ) {
          is OperationResult.Success -> {
            collected += result.data.items

            when (result.data.items.isEmpty() || collected.size >= result.data.totalItems) {
              true -> break
              false -> Unit
            }
          }

          is OperationResult.Error -> {
            break
          }
        }

        pageNumber += 1
      }

      return collected
    }
  }
