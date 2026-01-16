package org.grakovne.lissen.lib.domain

data class Bookmark(
	val libraryItemId: String,
	val title: String,
	val totalPosition: Double,
	val createdAt: Long
)

fun Bookmark.isSame(other: Bookmark): Boolean =
	libraryItemId == other.libraryItemId &&
		title == other.title &&
		totalPosition == other.totalPosition &&
		createdAt == other.createdAt
