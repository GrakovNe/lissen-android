package org.grakovne.lissen.domain

/**
 * Chapter marks are navigation points, not boundaries: the audio that follows the last mark
 * still plays, as it does in the web player. A chapter list ending before the audio gets its
 * tail attached to the last chapter, so playback, duration and progress see the whole audio.
 * A chapter list that already reaches or outlasts the audio is returned unchanged.
 */
fun List<PlayingChapter>.extendedToAudioEnd(audioDuration: Double): List<PlayingChapter> {
  val last = lastOrNull() ?: return this
  if (audioDuration <= last.end) return this

  return dropLast(1) + last.copy(end = audioDuration, duration = audioDuration - last.start)
}
