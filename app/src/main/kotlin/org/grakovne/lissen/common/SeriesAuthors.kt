package org.grakovne.lissen.common

fun combineAuthors(authors: List<String?>): String? =
  authors
    .asSequence()
    .filterNotNull()
    .flatMap { it.split(",") }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(", ")
    .ifBlank { null }
