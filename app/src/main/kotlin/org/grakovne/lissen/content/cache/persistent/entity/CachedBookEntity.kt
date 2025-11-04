package org.grakovne.lissen.content.cache.persistent.entity

import androidx.annotation.Keep
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.squareup.moshi.JsonClass
import java.io.Serializable

@Keep
@JsonClass(generateAdapter = true)
data class CachedBookEntity(
  @Embedded val detailedBook: BookEntity,
  @Relation(
    parentColumn = "id",
    entityColumn = "bookId",
  )
  val files: List<BookFileEntity>,
  @Relation(
    parentColumn = "id",
    entityColumn = "bookId",
  )
  val chapters: List<BookChapterEntity>,
  @Relation(
    parentColumn = "id",
    entityColumn = "bookId",
  )
  val progress: MediaProgressEntity?,
)

@Keep
@Entity(tableName = "detailed_books")
@JsonClass(generateAdapter = true)
data class BookEntity(
  @PrimaryKey val id: String,
  val title: String,
  val subtitle: String?,
  val author: String?,
  val narrator: String?,
  val year: String?,
  val abstract: String?,
  val publisher: String?,
  val duration: Int,
  val libraryId: String?,
  val seriesJson: String?, // List<BookSeriesDto> Json
  val seriesNames: String?,
  val createdAt: Long,
  val updatedAt: Long,
) : Serializable

@Keep
@Entity(
  tableName = "book_files",
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["bookId"])],
)
@JsonClass(generateAdapter = true)
data class BookFileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val bookFileId: String,
  val name: String,
  val duration: Double,
  val mimeType: String,
  val bookId: String,
) : Serializable

@Keep
@Entity(
  tableName = "book_chapters",
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["bookId"])],
)
@JsonClass(generateAdapter = true)
data class BookChapterEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val bookChapterId: String,
  val duration: Double,
  val start: Double,
  val end: Double,
  val title: String,
  val bookId: String,
  val isCached: Boolean,
) : Serializable

@Keep
@Entity(
  tableName = "media_progress",
  foreignKeys = [
    ForeignKey(
      entity = BookEntity::class,
      parentColumns = ["id"],
      childColumns = ["bookId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
  indices = [Index(value = ["bookId"])],
)
@JsonClass(generateAdapter = true)
data class MediaProgressEntity(
  @PrimaryKey val bookId: String,
  val currentTime: Double,
  val isFinished: Boolean,
  val lastUpdate: Long,
) : Serializable

@Keep
@JsonClass(generateAdapter = true)
data class BookSeriesDto(
  val title: String,
  val sequence: String?,
)
