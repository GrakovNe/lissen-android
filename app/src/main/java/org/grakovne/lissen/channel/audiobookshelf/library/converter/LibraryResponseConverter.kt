package org.grakovne.lissen.channel.audiobookshelf.library.converter

import org.grakovne.lissen.channel.audiobookshelf.common.model.LibraryResponse
import org.grakovne.lissen.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryResponseConverter @Inject constructor() {

    fun apply(response: LibraryResponse): List<Library> =
        response.libraries.map { Library(it.id, it.name) }
}
