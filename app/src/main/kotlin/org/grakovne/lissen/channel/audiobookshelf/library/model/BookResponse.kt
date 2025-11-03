package org.grakovne.lissen.channel.audiobookshelf.library.model

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class BookResponse(
  val id: String,
  val ino: String,
  val libraryId: String,
  val media: BookMedia,
  val addedAt: Long,
  val ctimeMs: Long,
)

@Keep
@JsonClass(generateAdapter = true)
data class BookMedia(
  val metadata: LibraryMetadataResponse,
  val audioFiles: List<BookAudioFileResponse>?,
  val chapters: List<LibraryChapterResponse>?,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryMetadataResponse(
  val title: String,
  val subtitle: String?,
  val authors: List<LibraryAuthorResponse>?,
  val narrators: List<String>?,
  val series: List<LibrarySeriesResponse>?,
  val description: String?,
  val publisher: String?,
  val publishedYear: String?,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibrarySeriesResponse(
  val id: String,
  val name: String,
  val sequence: String?,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryAuthorResponse(
  val id: String,
  val name: String,
)

@Keep
@JsonClass(generateAdapter = true)
data class BookAudioFileResponse(
  val index: Int,
  val ino: String,
  val duration: Double,
  val metadata: AudioFileMetadata,
  val metaTags: AudioFileTag?,
  val mimeType: String,
)

@Keep
@JsonClass(generateAdapter = true)
data class AudioFileMetadata(
  val filename: String,
  val ext: String,
  val size: Long,
)

@Keep
@JsonClass(generateAdapter = true)
data class AudioFileTag(
  val tagTitle: String?,
)

@Keep
@JsonClass(generateAdapter = true)
data class LibraryChapterResponse(
  val start: Double,
  val end: Double,
  val title: String,
  val id: String,
)
