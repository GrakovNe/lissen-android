package org.grakovne.lissen.domain

data class PagedItems<T>(
    val items: List<T>,
    val currentPage: Int,
)
