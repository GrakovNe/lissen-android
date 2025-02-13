package org.grakovne.lissen.domain

data class Book(
    val id: String,
    val subtitle: String?,
    val title: String,
    val author: String?,
    val duration: Int,
)
