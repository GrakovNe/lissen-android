package org.grakovne.lissen.channel.audiobookshelf.podcast.converter

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingDirection
import org.grakovne.lissen.common.LibraryOrderingOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PodcastOrderingRequestConverterTest {
  private val converter = PodcastOrderingRequestConverter()

  @ParameterizedTest
  @MethodSource("optionMappings")
  fun `maps ordering option to audiobookshelf field`(
    option: LibraryOrderingOption,
    expectedField: String,
  ) {
    val result = converter.apply(LibraryOrderingConfiguration(option, LibraryOrderingDirection.ASCENDING))

    assertEquals(expectedField, result.first)
  }

  @ParameterizedTest
  @MethodSource("directionMappings")
  fun `maps ordering direction to audiobookshelf flag`(
    direction: LibraryOrderingDirection,
    expectedFlag: String,
  ) {
    val result = converter.apply(LibraryOrderingConfiguration(LibraryOrderingOption.TITLE, direction))

    assertEquals(expectedFlag, result.second)
  }

  companion object {
    @JvmStatic
    fun optionMappings(): Stream<Arguments> =
      Stream.of(
        Arguments.of(LibraryOrderingOption.TITLE, "media.metadata.title"),
        Arguments.of(LibraryOrderingOption.AUTHOR, "media.metadata.author"),
        Arguments.of(LibraryOrderingOption.CREATED_AT, "addedAt"),
        Arguments.of(LibraryOrderingOption.UPDATED_AT, "mtimeMs"),
      )

    @JvmStatic
    fun directionMappings(): Stream<Arguments> =
      Stream.of(
        Arguments.of(LibraryOrderingDirection.ASCENDING, "0"),
        Arguments.of(LibraryOrderingDirection.DESCENDING, "1"),
      )
  }
}
