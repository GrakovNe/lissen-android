package org.grakovne.lissen.content.cache.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.gson.Gson
import org.grakovne.lissen.content.cache.entity.BookChapterEntity
import org.grakovne.lissen.content.cache.entity.BookEntity
import org.grakovne.lissen.content.cache.entity.BookFileEntity
import org.grakovne.lissen.content.cache.entity.BookSeriesDto
import org.grakovne.lissen.content.cache.entity.CachedBookEntity
import org.grakovne.lissen.content.cache.entity.MediaProgressEntity
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlayingChapter

@Dao
interface CachedBookDao {
    @Transaction
    suspend fun upsertCachedBook(
        book: DetailedItem,
        fetchedChapters: List<PlayingChapter>,
    ) {
        val bookEntity =
            BookEntity(
                id = book.id,
                title = book.title,
                subtitle = book.subtitle,
                author = book.author,
                narrator = book.narrator,
                duration = book.chapters.sumOf { it.duration }.toInt(),
                libraryId = book.libraryId,
                year = book.year,
                abstract = book.abstract,
                publisher = book.publisher,
                createdAt = book.createdAt,
                updatedAt = book.updatedAt,
                seriesNames =
                    book
                        .series
                        .joinToString(" ") { it.name },
                seriesJson =
                    book
                        .series
                        .map { BookSeriesDto(title = it.name, sequence = it.serialNumber) }
                        .let { gson.toJson(it) },
            )

        val bookFiles =
            book
                .files
                .map { file ->
                    BookFileEntity(
                        bookFileId = file.id,
                        name = file.name,
                        duration = file.duration,
                        mimeType = file.mimeType,
                        bookId = book.id,
                    )
                }

        val cachedBookChapters =
            fetchCachedBook(book.id)
                ?.chapters
                ?: emptyList()

        val bookChapters =
            book
                .chapters
                .map { chapter ->
                    BookChapterEntity(
                        bookChapterId = chapter.id,
                        duration = chapter.duration,
                        start = chapter.start,
                        end = chapter.end,
                        title = chapter.title,
                        bookId = book.id,
                        isCached =
                            fetchedChapters.any { it.id == chapter.id } ||
                                cachedBookChapters.any { it.bookChapterId == chapter.id && it.isCached },
                    )
                }

        val mediaProgress =
            book
                .progress
                ?.let { progress ->
                    MediaProgressEntity(
                        bookId = book.id,
                        currentTime = progress.currentTime,
                        isFinished = progress.isFinished,
                        lastUpdate = progress.lastUpdate,
                    )
                }

        upsertBook(bookEntity)
        upsertBookFiles(bookFiles)
        upsertBookChapters(bookChapters)
        mediaProgress?.let { upsertMediaProgress(it) }
    }

    @Transaction
    @RawQuery
    suspend fun fetchCachedBooks(query: SupportSQLiteQuery): List<BookEntity>

    @Transaction
    @RawQuery
    suspend fun searchBooks(query: SupportSQLiteQuery): List<BookEntity>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT * FROM detailed_books 
        INNER JOIN media_progress ON detailed_books.id = media_progress.bookId WHERE (libraryId IS NULL OR libraryId = :libraryId) 
        ORDER BY media_progress.lastUpdate DESC
        LIMIT 10
    """,
    )
    suspend fun fetchRecentlyListenedCachedBooks(libraryId: String?): List<BookEntity>

    @Transaction
    @Query("SELECT * FROM detailed_books WHERE id = :bookId")
    suspend fun fetchCachedBook(bookId: String): CachedBookEntity?

    @Query("SELECT COUNT(*) > 0 FROM detailed_books WHERE id = :bookId")
    fun isBookCached(bookId: String): LiveData<Boolean>

    @Query(
        """
    SELECT * FROM detailed_books
    LIMIT :pageSize
    OFFSET (:pageNumber * :pageSize)
    """,
    )
    suspend fun fetchCachedItems(
        pageSize: Int,
        pageNumber: Int,
    ): List<CachedBookEntity>

    @Query(
        """
    SELECT COUNT(*) > 0
    FROM book_chapters
    WHERE bookId       = :bookId
      AND bookChapterId = :chapterId
      AND isCached      = 1
    """,
    )
    fun isBookChapterCached(
        bookId: String,
        chapterId: String,
    ): LiveData<Boolean>

    @Transaction
    @Query("SELECT * FROM detailed_books WHERE id = :bookId")
    suspend fun fetchBook(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookFiles(files: List<BookFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookChapters(chapters: List<BookChapterEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaProgress(progress: MediaProgressEntity)

    @Transaction
    @Query("SELECT * FROM media_progress WHERE bookId = :bookId")
    suspend fun fetchMediaProgress(bookId: String): MediaProgressEntity?

    @Update
    suspend fun updateMediaProgress(progress: MediaProgressEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    companion object {
        val gson = Gson()
    }
}
