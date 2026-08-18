package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem

/**
 * Reports whether this book can produce a non-empty playback queue.
 *
 * PlaybackService.bookToChapterMediaItems resolves chapters against files and returns an
 * empty queue when either list is empty, so both must be non-empty for the book to be
 * resumable.
 */
internal fun DetailedItem.canProducePlaybackQueue(): Boolean = chapters.isNotEmpty() && files.isNotEmpty()
