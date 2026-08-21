package org.grakovne.lissen.content.cache.persistent

internal class CachingServiceSessionLifecycle {
  private var latestStartId = 0
  private var finishing = false

  fun accept(startId: Int) {
    latestStartId = startId
    finishing = false
  }

  fun beginFinish(): Int? {
    if (finishing) return null

    finishing = true
    return latestStartId
  }
}
