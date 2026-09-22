package org.grakovne.lissen.playback.service

import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackSession
import org.grakovne.lissen.domain.PlaybackSessionSource

/**
 * Everything the playback synchronization knows about what is being synced.
 * Transitions are pure; the effects they imply for the offline session uploader
 * are derived from the difference between two states by [uploaderEffects].
 */
internal data class SyncState(
  val item: DetailedItem? = null,
  val chapterIndex: Int? = null,
  val session: PlaybackSession? = null,
  val authenticated: Boolean = false,
) {
  val localSession: PlaybackSession?
    get() = session?.takeIf { it.sessionSource == PlaybackSessionSource.LOCAL }

  /** Being logged in is account state, not playback state, so it survives item changes. */
  fun start(item: DetailedItem): SyncState = SyncState(item = item, authenticated = authenticated)

  fun cancel(): SyncState = SyncState(authenticated = authenticated)

  /** Without an account nothing can be recorded, so the local session is handed over as well. */
  fun withAccount(authenticated: Boolean): SyncState =
    when (authenticated) {
      true -> copy(authenticated = true)
      false -> releaseLocal().copy(authenticated = false)
    }

  fun withChapter(chapterIndex: Int): SyncState = copy(chapterIndex = chapterIndex)

  /** A session opened for another item, say after a late server reply, is ignored. */
  fun adopt(
    opened: PlaybackSession,
    localSession: LocalSessionPolicy,
  ): SyncState =
    when (item?.id == opened.itemId) {
      true -> copy(session = choosePlaybackSession(session, opened, localSession))
      false -> this
    }

  /** Hands the local session over to the uploader while keeping a remote one as is. */
  fun releaseLocal(): SyncState = copy(session = session?.takeIf { it.sessionSource == PlaybackSessionSource.REMOTE })

  fun sessionStale(
    itemId: String,
    chapterIndex: Int,
  ): Boolean = session == null || session.itemId != itemId || chapterIndex != this.chapterIndex
}

/** What happens to the local session being written when the server answers with another local one. */
internal enum class LocalSessionPolicy {
  /** A retry of the same chapter: keep accumulating into the existing row. */
  KEEP,

  /** The item or chapter changed: start a fresh row. */
  REPLACE,
}

internal fun choosePlaybackSession(
  previous: PlaybackSession?,
  opened: PlaybackSession,
  localSession: LocalSessionPolicy,
): PlaybackSession {
  val previousLocal = previous?.takeIf { it.sessionSource == PlaybackSessionSource.LOCAL && it.itemId == opened.itemId }

  return when {
    opened.sessionSource == PlaybackSessionSource.LOCAL && localSession == LocalSessionPolicy.KEEP -> previousLocal ?: opened
    else -> opened
  }
}

internal sealed interface UploaderEffect {
  val sessionId: String

  /** The row is being written and must not be uploaded yet. */
  data class Activate(
    override val sessionId: String,
  ) : UploaderEffect

  /** The row is complete and can go to the server. */
  data class Release(
    override val sessionId: String,
  ) : UploaderEffect
}

/** A local session that stopped being the current one is released before the next one is activated. */
internal fun uploaderEffects(
  before: SyncState,
  after: SyncState,
): List<UploaderEffect> {
  val previousId = before.localSession?.sessionId
  val currentId = after.localSession?.sessionId

  if (previousId == currentId) return emptyList()

  return listOfNotNull(
    previousId?.let { UploaderEffect.Release(it) },
    currentId?.let { UploaderEffect.Activate(it) },
  )
}
