package org.grakovne.lissen.common

data class LibraryOrderingConfiguration(
    val option: LibraryOrderingOption,
    val direction: LibraryOrderingDirection,
) {

    companion object {

        val default = LibraryOrderingConfiguration(
            option = LibraryOrderingOption.TITLE,
            direction = LibraryOrderingDirection.ASCENDING,
        )
    }
}
