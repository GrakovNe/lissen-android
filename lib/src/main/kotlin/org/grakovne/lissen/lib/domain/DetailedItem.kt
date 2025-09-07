package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
data class DetailedItem(
  val id: String,
  val title: String,
  val subtitle: String?,
  val author: String?,
  val narrator: String?,
  val publisher: String?,
  val series: List<BookSeries>,
  val year: String?,
  val abstract: String?,
  val files: List<BookFile>,
  val chapters: List<PlayingChapter>,
  val progress: MediaProgress?,
  val libraryId: String?,
  val localProvided: Boolean,
  val createdAt: Long,
  val updatedAt: Long,
) : Serializable

@Keep
data class BookFile(
  val id: String,
  val name: String,
  val duration: Double,
  val mimeType: String,
) : Serializable

@Keep
data class MediaProgress(
  val currentTime: Double,
  val isFinished: Boolean,
  val lastUpdate: Long,
) : Serializable

@Keep
data class PlayingChapter(
  val available: Boolean,
  val podcastEpisodeState: BookChapterState?,
  val duration: Double,
  val start: Double,
  val end: Double,
  val title: String,
  val id: String,
) : Serializable

@Keep
data class BookSeries(
  val serialNumber: String?,
  val name: String,
) : Serializable

@Keep
enum class BookChapterState {
  FINISHED,
}
