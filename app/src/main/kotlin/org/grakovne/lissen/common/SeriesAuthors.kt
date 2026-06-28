package org.grakovne.lissen.common

fun mergeAuthorNames(authors: List<String?>): String? =
  authors
    .asSequence()
    .filterNotNull()
    .flatMap { it.split(",") }
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .distinct()
    .joinToString(", ")
    .ifBlank { null }
