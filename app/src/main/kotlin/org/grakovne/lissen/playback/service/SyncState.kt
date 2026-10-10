package org.grakovne.lissen.playback.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

/** What the synchronization service is syncing. All transitions return new states. */
data class SyncState(
  val item: DetailedItem? = null,
  val chapterIndex: Int? = null,
  val session: PlaybackSession? = null,
  val engaged: Boolean = false,
) {
  val localSession: PlaybackSession?
    get() = session?.takeIf { it.isLocal }

  fun start(item: DetailedItem): SyncState = SyncState(item = item)

  /** The user has sought or means to play. */
  fun engage(): SyncState = copy(engaged = true)

  fun cancel(): SyncState = SyncState()

  /** A session for another item is ignored. A local session for a chapter already written locally means a failed server retry, and its row is kept. */
  fun adopt(
    opened: PlaybackSession,
    chapterIndex: Int,
  ): SyncState =
    when {
      item?.id != opened.itemId -> this
      opened.isLocal && localSession != null && sessionStale(opened.itemId, chapterIndex).not() -> this
      else -> copy(session = opened, chapterIndex = chapterIndex)
    }

  /** Removes the local session so the uploader can take it. A remote session is kept as is. */
  fun releaseLocal(): SyncState = copy(session = session?.takeUnless { it.isLocal })

  fun sessionStale(
    itemId: String,
    chapterIndex: Int,
  ): Boolean = session == null || session.itemId != itemId || chapterIndex != this.chapterIndex
}

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
