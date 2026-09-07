package org.grakovne.lissen.playback.service

import android.net.Uri

fun toLissenUri(
  mediaItemId: String,
  fileId: String,
): Uri =
  Uri
    .Builder()
    .scheme("lissen")
    .appendPath(mediaItemId)
    .appendPath(fileId)
    .build()

fun parseLissenUri(uri: Uri): Pair<String, String>? {
  if (uri.scheme != "lissen") return null

  val segments = uri.pathSegments
  if (segments.size != 2) return null

  val mediaItemId = segments[0].takeIf { it.isNotEmpty() } ?: return null
  val fileId = segments[1].takeIf { it.isNotEmpty() } ?: return null

  return mediaItemId to fileId
}
