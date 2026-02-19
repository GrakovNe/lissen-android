package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.grakovne.lissen.lib.domain.LibraryType

class FetchRequestBuilder {
  private var libraryId: String? = null
  private var libraryType: LibraryType? = null
  private var pageNumber: Int = 0
  private var pageSize: Int = 20
  private var orderField: String = "title"
  private var orderDirection: String = "ASC"
  private var hideCompleted: Boolean = false

  fun libraryId(id: String?) = apply { this.libraryId = id }

  fun libraryType(type: LibraryType?) = apply { this.libraryType = type }

  fun pageNumber(number: Int) = apply { this.pageNumber = number }

  fun pageSize(size: Int) = apply { this.pageSize = size }

  fun orderField(field: String) = apply { this.orderField = field }

  fun orderDirection(direction: String) = apply { this.orderDirection = direction }

  fun hideCompleted(value: Boolean) = apply { this.hideCompleted = value }

  fun build(): SupportSQLiteQuery {
    val args = mutableListOf<Any>()

    val libraryWhereClause =
      when (val id = libraryId) {
        null -> {
          "b.libraryId IS NULL"
        }

        else -> {
          args.add(id)
          "(b.libraryId = ? OR b.libraryId IS NULL)"
        }
      }

    val hideCompletedWhereClause =
      when {
        hideCompleted && libraryType == LibraryType.LIBRARY -> "AND (mp.isFinished = 0 OR mp.isFinished IS NULL)"
        else -> ""
      }

    val field =
      when (orderField) {
        "title" -> "b.title"
        "author" -> "b.author"
        "duration" -> "b.duration"
        else -> "b.title"
      }

    val direction =
      when (orderDirection.uppercase()) {
        "ASC", "DESC" -> orderDirection.uppercase()
        else -> "ASC"
      }

    args.add(pageSize)
    args.add(pageNumber * pageSize)

    val sql =
      """
      SELECT b.*
      FROM detailed_books b
      LEFT JOIN media_progress mp ON mp.bookId = b.id
      WHERE $libraryWhereClause $hideCompletedWhereClause
      ORDER BY $field $direction
      LIMIT ? OFFSET ?
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }
}
