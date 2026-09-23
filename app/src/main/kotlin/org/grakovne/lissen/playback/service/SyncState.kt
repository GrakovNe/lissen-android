package org.grakovne.lissen.playback.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.grakovne.lissen.domain.DetailedItem
import org.grakovne.lissen.domain.PlaybackSession
import javax.inject.Inject
import javax.inject.Singleton

/** What the synchronization is syncing; transitions are pure. */
data class SyncState(
  val item: DetailedItem? = null,
  val chapterIndex: Int? = null,
  val session: PlaybackSession? = null,
) {
  val localSession: PlaybackSession?
    get() = session?.takeIf { it.isLocal }

  fun start(item: DetailedItem): SyncState = SyncState(item = item)

  fun cancel(): SyncState = SyncState()

  /** A session for another item is ignored; a local one for the chapter already written locally is a failed server retry and keeps the row. */
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
