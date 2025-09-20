package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

class SearchRequestBuilder {
  private var libraryId: String? = null
  private var searchQuery: String = ""
  private var orderField: String = "title"
  private var orderDirection: String = "ASC"

  fun libraryId(id: String?) = apply { this.libraryId = id }

  fun searchQuery(query: String) = apply { this.searchQuery = query }

  fun orderField(field: String) = apply { this.orderField = field }

  fun orderDirection(direction: String) = apply { this.orderDirection = direction }

  fun build(): SupportSQLiteQuery {
    val args = mutableListOf<Any>()

    val whereClause =
      buildString {
        when (val libraryId = libraryId) {
          null -> append("(libraryId IS NULL)")
          else -> {
            append("(libraryId = ? OR libraryId IS NULL)")
            args.add(libraryId)
          }
        }
        append(" AND (title LIKE ? OR author LIKE ? OR seriesNames LIKE ?)")
        val pattern = "%$searchQuery%"
        args.add(pattern)
        args.add(pattern)
        args.add(pattern)
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

    val sql =
      """
      SELECT * FROM detailed_books
      WHERE $whereClause
      ORDER BY $field $direction
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }
}
