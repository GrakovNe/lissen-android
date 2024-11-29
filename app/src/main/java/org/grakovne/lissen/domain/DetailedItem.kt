package org.grakovne.lissen.domain

import java.io.Serializable

data class DetailedItem(
    val id: String,
    val title: String,
    val author: String?,
    val files: List<BookFile>,
    val chapters: List<BookChapter>,
    val progress: MediaProgress?,
    val libraryId: String?,
    val localProvided: Boolean,
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

data class BookChapter(
    val available: Boolean,
    val duration: Double,
    val start: Double,
    val end: Double,
    val title: String,
    val id: String,
) : Serializable
