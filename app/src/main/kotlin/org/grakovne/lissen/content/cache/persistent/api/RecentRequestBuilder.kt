package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

class RecentRequestBuilder {
  private var libraryId: String? = null
  private var downloadedOnly: Boolean = false
  private var limit: Int = 10

  fun libraryId(id: String?) = apply { this.libraryId = id }

  fun downloadedOnly(enabled: Boolean) = apply { this.downloadedOnly = enabled }

  fun limit(limit: Int) = apply { this.limit = limit }

  fun build(): SupportSQLiteQuery {
    val args = mutableListOf<Any>()

    val whereClause =
      when (val libraryId = libraryId) {
        null -> "libraryId IS NULL"
        else -> {
          args.add(libraryId)
          "(libraryId = ? OR libraryId IS NULL)"
        }
      }

    val joinClause =
      when (downloadedOnly) {
        true -> "INNER JOIN book_chapters ON detailed_books.id = book_chapters.bookId AND book_chapters.isCached = 1"
        false -> ""
      }

    val sql =
      """
      SELECT DISTINCT detailed_books.* FROM detailed_books
      INNER JOIN media_progress ON detailed_books.id = media_progress.bookId
      $joinClause
      WHERE $whereClause
      AND media_progress.currentTime > 1.0
      AND media_progress.isFinished = 0
      ORDER BY media_progress.lastUpdate DESC
      LIMIT $limit
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }
}
