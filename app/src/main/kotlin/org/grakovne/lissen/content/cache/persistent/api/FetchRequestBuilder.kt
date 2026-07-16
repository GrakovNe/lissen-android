package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.grakovne.lissen.domain.LibraryType

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
    val (join, whereClause) = clauses(args)

    val field = "b.${resolveOrderField(orderField)}"
    val direction = resolveOrderDirection(orderDirection)

    args.add(pageSize)
    args.add(pageNumber * pageSize)

    val sql =
      """
      SELECT b.*
      FROM detailed_books b
      $join
      WHERE $whereClause
      ORDER BY $field $direction
      LIMIT ? OFFSET ?
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }

  fun buildCount(): SupportSQLiteQuery {
    val args = mutableListOf<Any>()
    val (join, whereClause) = clauses(args)

    val sql =
      """
      SELECT COUNT(*)
      FROM detailed_books b
      $join
      WHERE $whereClause
      """.trimIndent()

    return SimpleSQLiteQuery(sql, args.toTypedArray())
  }

  private fun clauses(args: MutableList<Any>): Pair<String, String> {
    val libraryWhereClause =
      when (val id = libraryId) {
        null -> {
          "b.libraryId IS NULL"
        }

        else -> {
          args.add(id)
          "(b.libraryId = ?)"
        }
      }

    val (join, filter) = hideCompletedSql(hideCompletedApplies(hideCompleted, libraryType), "b.id")

    return join to "$libraryWhereClause $filter"
  }
}
