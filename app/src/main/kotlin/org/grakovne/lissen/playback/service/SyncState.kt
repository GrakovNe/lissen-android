package org.grakovne.lissen.playback.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the playback synchronization knows about what is being synced.
 * Transitions are pure; the uploader derives the session still being written from it.
 */
data class SyncState(
  val item: DetailedItem? = null,
  val chapterIndex: Int? = null,
  val session: PlaybackSession? = null,
) {
  val localSession: PlaybackSession?
    get() = session?.takeIf { it.isLocal }

  fun start(item: DetailedItem): SyncState = SyncState(item = item)

  fun cancel(): SyncState = SyncState()

  /**
   * A session opened for another item, say after a late server reply, is ignored. A local
   * session opened for the chapter already being written locally is a failed retry of the
   * server: the existing row keeps accumulating. Anything else replaces the session.
   */
  fun adopt(
    opened: PlaybackSession,
    chapterIndex: Int,
  ): SyncState =
    when {
      item?.id != opened.itemId -> this
      opened.isLocal && localSession != null && sessionStale(opened.itemId, chapterIndex).not() -> this
      else -> copy(session = opened, chapterIndex = chapterIndex)
    }

  /** Hands the local session over to the uploader while keeping a remote one as is. */
  fun releaseLocal(): SyncState = copy(session = session?.takeUnless { it.isLocal })

  fun sessionStale(
    itemId: String,
    chapterIndex: Int,
  ): Boolean = session == null || session.itemId != itemId || chapterIndex != this.chapterIndex
}

/** The single place the state lives, shared by the playback synchronization and the uploader. */
@Singleton
class SyncStateStore
  @Inject
  constructor() {
    private val store = MutableStateFlow(SyncState())

    val state: StateFlow<SyncState> = store.asStateFlow()

    val value: SyncState
      get() = store.value

    fun update(transition: (SyncState) -> SyncState): SyncState = store.updateAndGet(transition)
  }
