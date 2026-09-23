package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem

/** The last seconds of an item count as finished: preparing playback there starts it over. */
const val ITEM_RESTART_WINDOW_SECONDS = 5.0

/** A last chapter shorter than the window does not put its own start inside it. */
fun DetailedItem.restartWindowStart(): Double? =
  chapters.lastOrNull()?.let { last -> last.end - minOf(ITEM_RESTART_WINDOW_SECONDS, last.duration) }

fun DetailedItem.isInRestartWindow(position: Double): Boolean = restartWindowStart()?.let { it < position } ?: false
