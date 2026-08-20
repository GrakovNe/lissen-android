package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem

internal fun DetailedItem.canProducePlaybackQueue(): Boolean = chapters.isNotEmpty() && files.isNotEmpty()
