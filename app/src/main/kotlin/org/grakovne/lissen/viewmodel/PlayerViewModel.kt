package org.grakovne.lissen.viewmodel

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.common.EpisodeOrderingConfiguration
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.domain.Book
import org.grakovne.lissen.domain.Bookmark
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.FinishedSuggestion
import org.grakovne.lissen.domain.LibraryType
import org.grakovne.lissen.domain.PlayingChapter
import org.grakovne.lissen.domain.TimerOption
import org.grakovne.lissen.domain.selectFinishedSuggestion
import org.grakovne.lissen.persistence.preferences.LibraryPreferences
import org.grakovne.lissen.persistence.preferences.PlaybackPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.autoskip.AutoSkipConfiguration
import org.grakovne.lissen.playback.autoskip.AutoSkipPreferences
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
@OptIn(UnstableApi::class)
class PlayerViewModel
  @Inject
  constructor(
    private val mediaRepository: MediaRepository,
    private val preferences: PlaybackPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val autoSkipPreferences: AutoSkipPreferences,
    private val session: SessionPreferences,
    private val mediaChannel: LissenMediaProvider,
  ) : ViewModel() {
    val book: StateFlow<DetailedItem?> = mediaRepository.playingBook

    val finishedBook: StateFlow<DetailedItem?> = mediaRepository.finishedBook

    private val _finishedSuggestion = MutableStateFlow<FinishedSuggestion?>(null)
    val finishedSuggestion: StateFlow<FinishedSuggestion?> = _finishedSuggestion.asStateFlow()

    fun loadFinishedSuggestion() {
      val book = mediaRepository.finishedBook.value ?: return
      val libraryId = book.libraryId ?: return
      val libraryType = book.libraryType ?: libraryPreferences.getPreferredLibraryType()

      if (libraryType != LibraryType.LIBRARY) {
        _finishedSuggestion.value = null
        return
      }

      viewModelScope.launch {
        _finishedSuggestion.value = resolveFinishedSuggestion(libraryId, book)
      }
    }

    private suspend fun resolveFinishedSuggestion(
      libraryId: String,
      book: DetailedItem,
    ): FinishedSuggestion? {
      book.series
        .firstOrNull { it.name.isNotBlank() }
        ?.id
        ?.let { seriesId ->
          val seriesBooks =
            mediaChannel
              .fetchSeriesItems(libraryId = libraryId, seriesId = seriesId)
              .fold(onSuccess = { it }, onFailure = { emptyList() })

          selectFinishedSuggestion(book, seriesBooks, emptyList(), emptyList())?.let { return it }
        }

      book.authors
        .firstOrNull { it.id.isNotBlank() }
        ?.let { author ->
          val authorBooks =
            mediaChannel
              .fetchBooks(
                libraryId = libraryId,
                pageSize = FINISHED_SUGGESTION_PAGE_SIZE,
                pageNumber = 0,
                extraFilter = AUTHOR_FILTER to author.id,
              ).fold(onSuccess = { it.items }, onFailure = { emptyList() })

          selectFinishedSuggestion(book, emptyList(), authorBooks, emptyList())?.let { return it }
        }

      val randomBooks = fetchNotStartedBooks(libraryId)

      return selectFinishedSuggestion(book, emptyList(), emptyList(), randomBooks)
    }

    private suspend fun fetchNotStartedBooks(libraryId: String): List<Book> {
      val collected = mutableListOf<Book>()
      var pageNumber = 0

      while (collected.size < FINISHED_RANDOM_BOOKS_POOL) {
        when (
          val result =
            mediaChannel.fetchBooks(
              libraryId = libraryId,
              pageSize = FINISHED_SUGGESTION_PAGE_SIZE,
              pageNumber = pageNumber,
              extraFilter = NOT_STARTED_FILTER,
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

    fun dismissFinishedBook() {
      Timber.d("User action: dismissFinishedBook")
      _finishedSuggestion.value = null
      mediaRepository.clearFinishedBook()
    }

    val preferredLibraryType: StateFlow<LibraryType> =
      libraryPreferences.preferredLibraryTypeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), libraryPreferences.getPreferredLibraryType())

    fun episodeOrdering(itemId: String): Flow<EpisodeOrderingConfiguration?> = libraryPreferences.episodeOrderingFlow.map { it[itemId] }

    fun autoSkip(itemId: String): Flow<AutoSkipConfiguration> = autoSkipPreferences.flow(itemId)

    fun setAutoSkip(
      itemId: String,
      configuration: AutoSkipConfiguration,
    ) {
      Timber.d("User action: setAutoSkip $configuration for $itemId")
      autoSkipPreferences.save(itemId, configuration)

      if (mediaRepository.playingBook.value?.id == itemId) mediaRepository.refreshTimer()
    }

    val currentChapterIndex: StateFlow<Int> = mediaRepository.currentChapterIndex
    val currentChapterPosition: StateFlow<Double> = mediaRepository.currentChapterPosition

    val currentChapterDuration: StateFlow<Double> = mediaRepository.currentChapterDuration
    val totalPosition: StateFlow<Double> = mediaRepository.totalPosition

    val timerOption: StateFlow<TimerOption?> = mediaRepository.timerOption
    val timerRemaining: StateFlow<Long?> = mediaRepository.timerRemaining

    private val _playingQueueExpanded = MutableStateFlow(false)
    val playingQueueExpanded: StateFlow<Boolean> = _playingQueueExpanded.asStateFlow()

    val isPlaybackReady: StateFlow<Boolean> = mediaRepository.isPlaybackReady
    val playbackSpeed: StateFlow<Float> = mediaRepository.playbackSpeed
    val preparingError: StateFlow<Boolean> = mediaRepository.mediaPreparingError

    private val _searchRequested = MutableStateFlow(false)
    val searchRequested: StateFlow<Boolean> = _searchRequested.asStateFlow()

    private val _searchToken = MutableStateFlow(EMPTY_SEARCH)
    val searchToken: StateFlow<String> = _searchToken.asStateFlow()

    val isPlaying: StateFlow<Boolean> = mediaRepository.isPlaying

    val bookmarks: StateFlow<List<Bookmark>> = mediaRepository.bookmarks

    fun createBookmark(title: String? = null) {
      Timber.d("User action: createBookmark at position=${totalPosition.value.toInt()}s")
      viewModelScope.launch {
        mediaRepository.createBookmark(title)
      }
    }

    fun dropBookmark(bookmark: Bookmark) {
      Timber.d("User action: dropBookmark at position=${bookmark.totalPosition.toInt()}s")
      viewModelScope.launch {
        mediaRepository.dropBookmark(bookmark = bookmark)
      }
    }

    fun updateBookmarks() {
      viewModelScope.launch { mediaRepository.updateBookmarks() }
    }

    fun hasCredentials() = session.hasCredentials()

    fun updatePlayingItem() {
      if (mediaRepository.playingBook.value != null) {
        return
      }

      val playingItem = preferences.getLastPlayingItem() ?: return

      viewModelScope.launch {
        mediaRepository.preparePlayback(playingItem.id, playingItem.libraryType)
      }
    }

    fun expandPlayingQueue() {
      _playingQueueExpanded.value = true
    }

    fun setTimer(option: TimerOption?) {
      Timber.d("User action: setTimer option=$option")
      mediaRepository.updateTimer(option)
    }

    fun collapsePlayingQueue() {
      _playingQueueExpanded.value = false
    }

    fun togglePlayingQueue() {
      _playingQueueExpanded.value = !_playingQueueExpanded.value
    }

    fun requestSearch() {
      _searchRequested.value = true
    }

    fun dismissSearch() {
      _searchRequested.value = false
      _searchToken.value = EMPTY_SEARCH
    }

    fun updateSearch(token: String) {
      _searchToken.value = token
    }

    fun preparePlayback(
      bookId: String,
      libraryType: LibraryType? = null,
    ) {
      viewModelScope.launch {
        mediaRepository.clearPreparedItem()
        mediaRepository.preparePlayback(bookId, libraryType)
      }
    }

    fun requiresBookPreparation(
      bookId: String,
      useLocalCache: Boolean,
    ): Boolean {
      val currentBook = book.value
      return currentBook?.id != bookId || currentBook.localProvided != useLocalCache
    }

    suspend fun openBook(
      bookId: String,
      libraryType: LibraryType?,
      useLocalCache: Boolean,
      playInstantly: Boolean,
      chapterId: String? = null,
    ) {
      val currentBook = book.value
      val preparationRequired = requiresBookPreparation(bookId, useLocalCache)

      val canStart =
        if (preparationRequired) {
          val storedBook = preferences.getLastPlayingItem()?.takeIf { it.id == bookId }

          mediaRepository.clearPreparedItem()
          mediaRepository.preparePlayback(
            bookId = bookId,
            libraryType = currentBook?.takeIf { it.id == bookId }?.libraryType ?: storedBook?.libraryType ?: libraryType,
            chapterId = chapterId,
          )
        } else {
          chapterId
            ?.let { id -> currentBook?.chapters?.firstOrNull { it.id == id } }
            ?.let { chapter ->

              mediaRepository.isPlaybackReady.first { it }
              setChapter(chapter)
            }
          true
        }

      if (playInstantly && canStart) {
        mediaRepository.playingBook.value
          ?.takeIf { it.id == bookId }
          ?.let(mediaRepository::prepareAndPlay)
      }
    }

    fun rewind() {
      Timber.d("User action: rewind at position=${totalPosition.value.toInt()}s")
      mediaRepository.rewind()
    }

    fun forward() {
      Timber.d("User action: forward at position=${totalPosition.value.toInt()}s")
      mediaRepository.forward()
    }

    fun seekTo(chapterPosition: Double) {
      Timber.d("User action: seekTo chapterPosition=${chapterPosition.toInt()}s")
      mediaRepository.setChapterPosition(chapterPosition)
    }

    fun setTotalPosition(totalPosition: Double) {
      Timber.d("User action: setTotalPosition ${totalPosition.toInt()}s")
      mediaRepository.setTotalPosition(totalPosition)
    }

    fun setChapter(chapter: PlayingChapter) {
      if (chapter.available) {
        val index = book.value?.chapters?.indexOf(chapter) ?: -1
        Timber.d("User action: setChapter '${chapter.title}' index=$index")
        mediaRepository.setChapter(index)
      }
    }

    fun clearPlayingBook() {
      Timber.d("User action: clearPlayingBook bookId=${book.value?.id}")
      mediaRepository.clearPlayingBook()
    }

    fun setPlaybackSpeed(factor: Float) {
      Timber.d("User action: setPlaybackSpeed $factor")
      mediaRepository.setPlaybackSpeed(factor)
    }

    fun nextTrack() {
      Timber.d("User action: nextTrack")
      mediaRepository.nextTrack()
    }

    fun previousTrack() {
      Timber.d("User action: previousTrack")
      mediaRepository.previousTrack()
    }

    fun togglePlayPause() {
      Timber.d("User action: togglePlayPause (isPlaying=${isPlaying.value})")
      mediaRepository.togglePlayPause()
    }

    fun canReorderPlayingItem(itemId: String): Boolean = mediaRepository.canReorderPlayingItem(itemId)

    fun setEpisodeOrdering(
      itemId: String,
      configuration: EpisodeOrderingConfiguration,
    ) {
      Timber.d("User action: setEpisodeOrdering $configuration for $itemId")

      val previous = libraryPreferences.getEpisodeOrdering(itemId)
      libraryPreferences.saveEpisodeOrdering(itemId, configuration)

      if (mediaRepository.reorderPlayingItem(itemId, configuration).not()) {
        when (previous) {
          null -> libraryPreferences.clearEpisodeOrdering(itemId)
          else -> libraryPreferences.saveEpisodeOrdering(itemId, previous)
        }
      }
    }

    companion object {
      private const val EMPTY_SEARCH = ""
      private const val FINISHED_SUGGESTION_PAGE_SIZE = 100
      private const val FINISHED_RANDOM_BOOKS_POOL = 200
      private const val AUTHOR_FILTER = "authors"
      private val NOT_STARTED_FILTER = "progress" to "not-started"
    }
  }
