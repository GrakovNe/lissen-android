package org.grakovne.lissen.wear.ui.screens.player

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.grakovne.lissen.lib.domain.BookSeries
import org.grakovne.lissen.lib.domain.DetailedItem
import org.grakovne.lissen.lib.domain.PlayingChapter
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel
  @Inject
constructor(
) : ViewModel() {
  val playingBook: LiveData<DetailedItem> = MutableLiveData(DetailedItem(
    id = "",
    title = "Harry Potter and the Philosophers Stone",
    subtitle = "Book 1",
    author = "J.K. Rowling",
    narrator = "Rufus Beck",
    publisher = "Verlag",
    series = listOf(BookSeries("", "Harry Potter")),
    year = "",
    abstract = "",
    files = emptyList(),
    chapters = listOf(PlayingChapter(available = true, podcastEpisodeState = null, duration = 260.0, start = 0.0, end = 0.0, title = "Chapter 1", id = "")),
    progress = null,
    libraryId = "",
    localProvided = true,
    createdAt = 0,
    updatedAt = 0
  ))
}