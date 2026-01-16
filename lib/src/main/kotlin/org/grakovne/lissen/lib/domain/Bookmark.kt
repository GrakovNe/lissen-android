package org.grakovne.lissen.lib.domain


data class Bookmark(
	val id: String,
	val libraryItemId: String,
	val title: String,
	val time: Int,
	val createdAt: Long
)