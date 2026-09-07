package org.grakovne.lissen.content.cache.persistent.api

fun resolveOrderField(field: String): String =
  when (field) {
    "author", "createdAt", "updatedAt" -> field
    else -> "title"
  }

fun resolveOrderDirection(direction: String): String =
  when (direction.uppercase()) {
    "ASC", "DESC" -> direction.uppercase()
    else -> "ASC"
  }
