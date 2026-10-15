package org.grakovne.lissen.content.cache.persistent.api

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import org.grakovne.lissen.channel.common.LibraryFilters
import org.grakovne.lissen.domain.LibraryType

class FetchRequestBuilder {
  private var libraryId: String? = null
  private var libraryType: LibraryType? = null
  private var pageNumber: Int = 0
  private var pageSize: Int = 20
  private var orderField: String = "title"
  private var orderDirection: String = "ASC"
  private var hideCompleted: Boolean = false
  private var extraFilter: Pair<String, String>? = null

  fun libraryId(id: String?) = apply { this.libraryId = id }

  fun libraryType(type: LibraryType?) = apply { this.libraryType = type }

  fun pageNumber(number: Int) = apply { this.pageNumber = number }

  fun pageSize(size: Int) = apply { this.pageSize = size }

  fun orderField(field: String) = apply { this.orderField = field }

  fun orderDirection(direction: String) = apply { this.orderDirection = direction }

  fun hideCompleted(value: Boolean) = apply { this.hideCompleted = value }

  fun extraFilter(filter: Pair<String, String>?) = apply { this.extraFilter = filter }

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
    val extra = extraFilterSql(extraFilter, args)

    return join to "$libraryWhereClause $filter $extra"
  }

  private fun extraFilterSql(
    filter: Pair<String, String>?,
    args: MutableList<Any>,
  ): String =
    filter
      ?.let { (key, value) ->
        when {
          key == LibraryFilters.AUTHORS -> {
            args.add("%\"id\":\"$value\"%")
            "AND (b.authorsJson LIKE ?)"
          }

          key == LibraryFilters.PROGRESS && value == LibraryFilters.NOT_STARTED -> {
            "AND (NOT EXISTS (SELECT 1 FROM media_progress mp2 WHERE mp2.bookId = b.id AND mp2.currentTime > 0))"
          }

          else -> {
            ""
          }
        }
      } ?: ""
}
