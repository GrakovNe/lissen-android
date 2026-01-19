package org.grakovne.lissen.lib.domain

enum class BookmarkSyncState {
	SYNCED,
	PENDING_CREATE,
	PENDING_DELETE;
}

fun BookmarkSyncState.asInteger(): Int = when (this) {
	BookmarkSyncState.SYNCED -> 1
	BookmarkSyncState.PENDING_CREATE -> 2
	BookmarkSyncState.PENDING_DELETE -> 3
}

fun Int.asBookmarkSyncState(): BookmarkSyncState = when (this) {
	1 -> BookmarkSyncState.SYNCED
	2 -> BookmarkSyncState.PENDING_CREATE
	3 -> BookmarkSyncState.PENDING_DELETE
	else -> BookmarkSyncState.PENDING_DELETE
}