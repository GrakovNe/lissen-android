package org.grakovne.lissen.common

import androidx.annotation.Keep
import androidx.compose.runtime.saveable.Saver
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class LibraryOrderingConfiguration(
  val option: LibraryOrderingOption,
  val direction: LibraryOrderingDirection,
) {
  companion object {
    val default =
      LibraryOrderingConfiguration(
        option = LibraryOrderingOption.TITLE,
        direction = LibraryOrderingDirection.ASCENDING,
      )

    val saver: Saver<LibraryOrderingConfiguration, *> =
      Saver(
        save = {
          listOf(it.option.name, it.direction.name)
        },
        restore = {
          LibraryOrderingConfiguration(
            option = LibraryOrderingOption.valueOf(it[0]),
            direction = LibraryOrderingDirection.valueOf(it[1]),
          )
        },
      )
  }
}
