package org.grakovne.lissen.domain

import java.io.Serializable

data class DetailedItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val author: String?,
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

data class BookFile(
    val id: String,
    val name: String,
    val duration: Double,
    val mimeType: String,
) : Serializable

data class MediaProgress(
    val currentTime: Double,
    val isFinished: Boolean,
    val lastUpdate: Long,
) : Serializable

data class PlayingChapter(
    val available: Boolean,
    val podcastEpisodeState: BookChapterState?,
    val duration: Double,
    val start: Double,
    val end: Double,
    val title: String,
    val id: String,
) : Serializable

data class BookSeries(
    val serialNumber: String?,
    val name: String,
) : Serializable

enum class BookChapterState {
    FINISHED,
}
