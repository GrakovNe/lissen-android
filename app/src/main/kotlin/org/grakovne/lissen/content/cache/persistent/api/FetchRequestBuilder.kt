package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

class FetchRequestBuilder {
  private var libraryId: String? = null
  private var pageNumber: Int = 0
  private var pageSize: Int = 20
  private var orderField: String = "title"
  private var orderDirection: String = "ASC"

  fun libraryId(id: String?) = apply { this.libraryId = id }

  fun pageNumber(number: Int) = apply { this.pageNumber = number }

  fun pageSize(size: Int) = apply { this.pageSize = size }

  fun orderField(field: String) = apply { this.orderField = field }

  fun orderDirection(direction: String) = apply { this.orderDirection = direction }

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

    val field =
      when (orderField) {
        "title", "author", "duration" -> orderField
        else -> "title"
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
      SELECT * FROM detailed_books
      WHERE $whereClause
      ORDER BY $field $direction
      LIMIT ? OFFSET ?
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }
}
