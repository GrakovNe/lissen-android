package org.grakovne.lissen.domain

data class CreateBookmarkRequest(
  val libraryItemId: String,
  val title: String,
  val time: Int,
)
