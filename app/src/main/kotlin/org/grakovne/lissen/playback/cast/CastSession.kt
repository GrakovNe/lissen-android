package org.grakovne.lissen.playback.cast

/** A UPnP MediaRenderer known to the jUPnP registry by its UDN. */
data class CastDevice(
  val id: String,
  val udn: String,
  val name: String,
  val host: String,
  val model: String? = null,
)

enum class CastSessionState {
  CONNECTING,
  CONNECTED,
  FAILED,
}

data class CastSession(
  val device: CastDevice,
  val state: CastSessionState,
  val message: String? = null,
)

/** What the renderer should fetch for one audio file of the playing book. */
data class CastMedia(
  val url: String,
  val mimeType: String,
  val title: String,
  val subtitle: String?,
  val artworkUrl: String?,
  val durationMs: Long,
  /** True when the URL holds only this clip, so renderer positions start at the clip start. */
  val clipped: Boolean = false,
)
