package org.grakovne.lissen.content.cache.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.io.Serializable

data class CachedBookEntity(
    @Embedded val detailedBook: BookEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val files: List<BookFileEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val chapters: List<BookChapterEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val progress: MediaProgressEntity?
)


@Entity(tableName = "detailed_books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val duration: Int,
) : Serializable

@Entity(
    tableName = "book_files",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId"])]
)
data class BookFileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val duration: Double,
    val mimeType: String,
    val bookId: String
) : Serializable

@Entity(
    tableName = "book_chapters",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId"])]
)
data class BookChapterEntity(
    @PrimaryKey val id: String,
    val duration: Double,
    val start: Double,
    val end: Double,
    val title: String,
    val bookId: String
) : Serializable

@Entity(
    tableName = "media_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId"])]
)
data class MediaProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val bookId: String,
    val currentTime: Double,
    val isFinished: Boolean,
    val lastUpdate: Long
) : Serializable