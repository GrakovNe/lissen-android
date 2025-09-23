package org.grakovne.lissen.lib.domain

enum class LibraryType {
  LIBRARY,
  PODCAST,
  UNKNOWN;
  
  
  companion object {
    val meaningfulTypes = listOf(LIBRARY, PODCAST)
  }
}