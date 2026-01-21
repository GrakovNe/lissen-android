package org.grakovne.lissen.content.cache.persistent.converter

import org.grakovne.lissen.content.cache.persistent.entity.MediaProgressEntity
import org.grakovne.lissen.lib.domain.MediaProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaProgressEntityConverter
  @Inject
  constructor() {
    fun apply(entity: MediaProgressEntity): MediaProgress =
      MediaProgress(
        currentTime = entity.currentTime,
        isFinished = entity.isFinished,
        lastUpdate = entity.lastUpdate,
      )
  }
