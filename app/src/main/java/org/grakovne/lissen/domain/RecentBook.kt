package org.grakovne.lissen.domain

data class RecentBook(
    val id: String,
    val title: String,
    val author: String?,
    val listenedPercentage: Int?,
)
