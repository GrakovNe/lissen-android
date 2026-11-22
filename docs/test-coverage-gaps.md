# Test coverage gaps (2026-11)

Audit of the whole app against the three suites that exist today:

- **unit** — `app/src/test` (JUnit 5, MockK, coroutines-test, MockWebServer; no Robolectric),
- **instrumented** — `app/src/androidTest` (Hilt, Room, Compose UI, media3 on an emulator),
- **E2E** — `minifiedTest` (UI Automator black-box flows against the R8-minified APK).

Method: every production file was checked for a same-named test or a real reference from any
test source (symbol-level, not just file names). 420 production files, ~224 test files.
Pure Compose widgets, `@Module` Hilt wiring, generated models, icons and placeholders make up
most of the "untested" list and are intentionally left out — they are covered indirectly by the
E2E suite, and unit-testing them would assert the framework, not the app.

## Gaps worth closing (in scope here)

### Unit (pure JVM logic with real branches, zero coverage)

| Production code | Why it matters |
|---|---|
| `ui/components/slider/SliderState` (`snapTo`, `snapToNearest`, `animateDecayTo`, `saver`) | Every settings slider (speed, seek, volume, equalizer) funnels through it; clamping and rounding bugs would silently write wrong preferences. |
| `persistence/preferences/PerItemPreferences` (`getPerItem`/`putPerItem`) | The "one unreadable entry drops only that entry" promise is the whole point of the custom parser; a regression loses every per-book setting. |
| `domain/ChapterAudioEnd.extendedToAudioEnd` | Decides whether playback, duration and progress see the audio tail after the last chapter mark. |
| `content/cache/common/FileKey.toFileKey` | Cache file naming: a collision or instability corrupts the offline cache. |
| `content/cache/common/BufferExtensions.writeToFile` | Every cached download lands on disk through it. |
| `util/FuturesUtil` (`asListenableFuture`, `listenableFuture`) | Bridges coroutines to media3 futures; a lost exception would hang the session handshake. |
| `ui/screens/settings/advanced/cache/CachedItemsPageSource` | Paging keys for the cached-items screen; wrong `nextPage`/`prevKey` loops or drops pages. |

### Integration

| Production code | Why it matters |
|---|---|
| `playback/service/PlaybackNavigationService` | Zero coverage, non-trivial branch logic: skipping unavailable tracks forward/backward, wrap-around detection, pausing at the end of the queue. Needs a real ExoPlayer, so it belongs in `app/src/androidTest` alongside `RealPlayerTest` — a JVM unit test cannot drive it. |
| `content/cache/persistent/Migrations` 14→26 | The migration test covered only single hops. A full chain test catches a broken link in the middle. (1→14 stays untested — see the note under *What was implemented*.) |
| `playback/MediaSessionConnection` deferred actions | Deferred-action draining before connect is invisible in unit tests; needs the real session. (Left as a follow-up: requires a live `PlaybackService`, high setup cost for the value; the `DeferredActions` unit itself is already covered.) |

### E2E (minified flows with no journey)

| Flow | Gap |
|---|---|
| Playing-queue tab | `PlayingQueueComposable` has no journey: opening the queue, its collapse, and the queue's own chapter navigation. |
| Bookmarks | Existing test only opens the sheet. No create → appears in list → seek-to → delete round trip, which is the only thing that proves bookmarks persist across the player. |
| Volume boost slider | `VolumeBoostSlider` + `DynamicsProcessingEqualizer` settings path has no journey at all. |
| Offline playback | Downloads tab is opened but nothing proves a downloaded chapter plays without the network — the core promise of the cache. |

## Explicitly out of scope

- Hilt `@Module`s, `@AndroidEntryPoint` activities, icons, placeholder/fallback composables —
  wiring, verified by compilation and the E2E suite.
- `ui/screens/**` presentational composables without logic — the logic that was extracted
  (`provideSpeedLabel`, `provideMiniPlayerSubtitle`, …) is already unit-tested; the rest is
  covered by E2E journeys.
- `AudioBookshelfSyncService`/`PodcastSyncService` interfaces — no behavior.
- `MediaSessionConnection` — see integration note above.

## What was implemented

Unit (`app/src/test`):

- `ui/components/slider/SliderStateTest` — snapping, clamping, decay target, saver round trip.
  The `coerceIn` inside `snapToNearest` is unreachable through the public API (`snapTo` clamps
  first), so that test covers the rounding it performs, not the clamp.
- `persistence/preferences/PerItemPreferencesTest` — the one-bad-entry-drops-only-that-entry
  parsing contract, empty/corrupt documents, put/get round trips.
- `domain/ChapterAudioEndTest` — tail attachment, boundary equality with a duration that
  differs from the chapter span, zero-duration guard.
- `content/cache/common/FileKeyTest` — known SHA-256 digests, stability, UTF-8 (not platform)
  encoding, filesystem-safe output.
- `content/cache/common/BufferExtensionsTest` — full write, empty buffer, overwrite, buffer
  consumption, missing-parent failure.
- `util/FuturesUtilTest` — value/error/cancellation propagation across the coroutine↔Guava
  bridge, including the deadlock-free scope for the exception path.
- `ui/screens/settings/advanced/cache/CachedItemsPageSourceTest` — first/next/last page keys,
  error propagation.

Integration (`app/src/androidTest`):

- `playback/PlaybackNavigationAcceptanceTest` — runs `PlaybackNavigationService` against a
  real ExoPlayer with a silent in-memory source: forward/backward step over an unplayable
  chapter, speed re-applied on resume, and what happens when the last playable chapter steps
  into a missing one. The queue items must carry the `chapter:<book>:<index>` media id the
  service parses, so this scenario uses its own silence factory that reads the duration from
  the uri — the shared `SilenceFactory` reads it from the media id and would throw.

  That factory has to call `updateMediaItem(mediaItem)` on the `SilenceMediaSource` it returns.
  The source ignores the item it was built for and publishes its own placeholder (mediaId
  `"SilenceMediaSource"`, no tag, no requestMetadata) into the timeline window, and ExoPlayer
  only calls `updateMediaItem` from `replaceMediaItems`, never from `setMediaItems`. Without the
  call the service reads a null tag off `currentMediaItem`, returns at its first guard, and skips
  nothing — the forward test then walks into chapter 3 on its own and fails the seek assertion,
  the backward test waits for chapter 0 until it times out, and the wrap test ends on index 3.

  Every player read also has to happen on the main thread — `onMain { ... }` or `awaitOnMain`,
  never inline in a test body — or ExoPlayer throws "Player is accessed on the wrong thread".
  `discontinuities` is a `CopyOnWriteArrayList` and is safe to read from the test thread.

  Asserting "still playing" needs care: `isPlaying` is false while a seek re-buffers, so checking
  it right after the index lands fails, and waiting for `!isPlaying` passes before the service has
  paused. `playWhenReady` is the signal the service actually flips — assert that for "did not
  pause" / "did pause", and await `isPlaying` separately once the seek has settled.

  One behaviour is asserted as-is rather than as-specified: when the search wraps around the
  queue, the service seeks to the wrapped chapter *and then* pauses, so the listener ends up
  paused at the start of the book instead of where they stopped. That may well be wrong —
  pausing without seeking is the more defensible contract — but it is what the code does, and
  the test pins it so a change is a deliberate one.

- `content/cache/persistent/LocalCacheStorageMigrationTest` — added `20→21` (the libraryId /
  seriesId indexes, verified through `EXPLAIN QUERY PLAN`), a full `20→26` chain carrying
  books through every step, and an offline-session write/read after the full chain.

  Migrations 1→14 are **not** testable as things stand, and this is not just missing effort:
  `MigrationTestHelper.createDatabase(1)` needs an exported schema JSON for version 1 of the
  current storage class, and `app/schemas/` only holds 14–26 for
  `content.cache.persistent.LocalCacheStorage` (10–14 exist under the pre-rename
  `content.cache.LocalCacheStorage`). Testing them means reconstructing those schemas by hand.
  A `14→26` chain is the practical ceiling until the old schemas are recovered.

E2E (`minifiedTest`):

- `PlayerBookmarksDownloadsE2ETest` — bookmark create → appears → delete round trip;
  download-current-chapter round trip; the next-chapters stepper round trip; volume-boost
  preset round trip asserted through the settings row subtitle.

  Two traps here are worth recording for the next person. The "Current chapter" option
  dismisses the sheet as part of requesting the download, so anything observed afterwards has
  to reopen it — and an absence assertion must be made against an *open* sheet, otherwise it
  only proves the sheet closed. And a small chapter can finish downloading before the sheet is
  reopened, so the test accepts either the stop action (still running) or the clear action
  (already finished) as proof the request took effect.

Not covered here (candidates for a follow-up): the playing-queue tab journey and a true
offline-playback journey (airplane-mode playback of a cached chapter), both of which need
device-side network control that the current E2E harness does not expose.

## Verification

- `./gradlew :app:testDebugUnitTest` — all green.
- `./gradlew :app:lintKotlinTest :app:lintKotlinAndroidTest` — clean.
- `./gradlew :app:compileDebugAndroidTestKotlin` and `:minifiedTest:assembleDebug` — compile
  only. **The instrumented and E2E suites were never executed here**: the dev sandbox has no
  `/dev/kvm`, so no emulator can boot. They run on CI. A review round caught four defects in
  these two files that a single emulator run would have caught immediately, so treat CI green
  as the real acceptance gate for them, not the compile.
- Mutation spot-checks were run against the unit tests to confirm each fails when the behavior
  breaks: the `SliderState` clamp (4 tests die), the `SliderState` saver bounds, the
  `PerItemPreferences` parsing fallback (3 tests die), and the `ChapterAudioEnd` boundary
  comparison (the duration-vs-span fixture catches the `<=`→`<` slip that value-equal fixtures
  would miss). The same check cannot be run against the instrumented and E2E suites without a
  device.
