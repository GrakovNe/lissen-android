package org.grakovne.lissen.playback.service

class MimeTypeProvider {
  companion object {
    fun getSupportedMimeTypes() =
      listOf(
        "audio/flac",
        "audio/mp4",
        "audio/aac",
        "audio/mpeg",
        "audio/mp3",
        "audio/webm",
        "audio/opus",
        "audio/ogg",
        "audio/vorbis",
        "audio/x-matroska",
      )
  }
}
