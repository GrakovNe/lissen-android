package org.grakovne.lissen.common

/**
 * Joins the authors of the books that belong to a series into a single line, removing duplicates.
 * Each provided value may already contain several comma-separated authors.
 */
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
