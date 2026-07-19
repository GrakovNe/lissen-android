package org.grakovne.lissen.content.cache.persistent.api

import org.grakovne.lissen.content.cache.persistent.converter.CachedLibraryEntityConverter
import org.grakovne.lissen.content.cache.persistent.dao.CachedLibraryDao
import org.grakovne.lissen.domain.Library
import org.grakovne.lissen.domain.LibraryType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedLibraryRepository
  @Inject
  constructor(
    private val dao: CachedLibraryDao,
    private val converter: CachedLibraryEntityConverter,
  ) {
    suspend fun cacheLibraries(libraries: List<Library>) = dao.updateLibraries(libraries)

    suspend fun fetchLibraryType(libraryId: String): LibraryType? = dao.fetchLibrary(libraryId)?.type

    suspend fun fetchLibraries() =
      dao
        .fetchLibraries()
        .map { converter.apply(it) }
  }
