package org.grakovne.lissen.playback

import org.grakovne.lissen.domain.DetailedItem

/**
 * The last seconds of an item count as "finished": preparing playback there starts the item
 * over. Everything that reasons about that window (the service applying it, a reorder keeping
 * a restored position clear of it) reads it from here.
 */
const val ITEM_RESTART_WINDOW_SECONDS = 5.0

/**
 * Where the restart window opens, or `null` for an item without chapters. A last chapter
 * shorter than the window does not put its own start inside it.
 */
fun DetailedItem.restartWindowStart(): Double? =
  chapters.lastOrNull()?.let { last -> last.end - minOf(ITEM_RESTART_WINDOW_SECONDS, last.duration) }

fun DetailedItem.isInRestartWindow(position: Double): Boolean = restartWindowStart()?.let { it < position } ?: false
