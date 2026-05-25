package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep

@Keep
enum class LibraryType {
  LIBRARY,
  PODCAST,
  UNKNOWN;
  
  
  companion object {
    val meaningfulTypes = listOf(LIBRARY, PODCAST)
  }
}