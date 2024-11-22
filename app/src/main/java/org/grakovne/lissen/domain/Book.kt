package org.grakovne.lissen.domain

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val duration: Int,
    val cachedState: BookCachedState,
)

enum class BookCachedState {
    ABLE_TO_CACHE,
    UNABLE_TO_CACHE,
    CACHED,
}
