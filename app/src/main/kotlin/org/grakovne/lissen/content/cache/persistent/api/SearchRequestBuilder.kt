package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

class SearchRequestBuilder {
  private var libraryId: String = ""
  private var searchQuery: String = ""
  private var orderField: String = "title"
  private var orderDirection: String = "ASC"
  private var limit: Int? = null

  fun libraryId(id: String) = apply { this.libraryId = id }

  fun searchQuery(query: String) = apply { this.searchQuery = query }

  fun orderField(field: String) = apply { this.orderField = field }

  fun orderDirection(direction: String) = apply { this.orderDirection = direction }

  fun limit(value: Int) = apply { this.limit = value }

  fun build(): SupportSQLiteQuery {
    val args = mutableListOf<Any>()

    val whereClause =
      buildString {
        append("(libraryId = ?)")
        args.add(libraryId)

        append(" AND (title LIKE ? OR author LIKE ? OR seriesNames LIKE ?)")
        val pattern = "%$searchQuery%"
        args.add(pattern)
        args.add(pattern)
        args.add(pattern)
      }

    val field = resolveOrderField(orderField)
    val direction = resolveOrderDirection(orderDirection)

    val limitClause =
      limit
        ?.let {
          args.add(it)
          "LIMIT ?"
        }
        ?: ""

    val sql =
      """
      SELECT * FROM detailed_books
      WHERE $whereClause
      ORDER BY $field $direction
      $limitClause
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }
}
