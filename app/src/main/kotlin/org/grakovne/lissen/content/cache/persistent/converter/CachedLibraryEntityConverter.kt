package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.CachedLibraryEntity
import org.grakovne.lissen.lib.domain.Library
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CachedLibraryEntityConverter
  @Inject
  constructor() {
    fun apply(entity: CachedLibraryEntity): Library =
      Library(
        id = entity.id,
        title = entity.title,
        type = entity.type,
      )
  }
