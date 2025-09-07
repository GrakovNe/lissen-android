package org.grakovne.lissen.content.cache.api

import org.grakovne.lissen.content.cache.converter.CachedLibraryEntityConverter
import org.grakovne.lissen.content.cache.dao.CachedLibraryDao
import org.grakovne.lissen.lib.domain.Library
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

    suspend fun fetchLibraries() =
      dao
        .fetchLibraries()
        .map { converter.apply(it) }
  }
