package org.grakovne.lissen.lib.domain

data class CreateBookmarkRequest (
	val libraryItemId: String,
	val title: String,
	val time: Int,
)