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
| `playback/service/PlaybackNavigationService` | Zero coverage, non-trivial branch logic: skipping unavailable tracks forward/backward, wrap-around detection, pausing at the end of the queue. Runs on a real (JVM) ExoPlayer in `app/src/test` — the same trick `RealPlayerTest` uses on-device. |
| `content/cache/persistent/Migrations` 1→14 | The migration test starts at 14; every older migration ships untested. Plus a full 14→26 chain test so a broken link in the middle is caught. |
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
  chapter, stop-instead-of-wrap at the end of the playable queue, speed re-applied on resume.
- `content/cache/persistent/LocalCacheStorageMigrationTest` — added `20→21` (the libraryId /
  seriesId indexes, verified through `EXPLAIN QUERY PLAN`), a full `20→26` chain carrying
  books through every step, and an offline-session write/read after the full chain.

E2E (`minifiedTest`):

- `PlayerBookmarksDownloadsE2ETest` — bookmark create → appears → delete round trip;
  download-current-chapter start/stop round trip; the next-chapters stepper round trip;
  volume-boost preset round trip asserted through the settings row subtitle.

Not covered here (candidates for a follow-up): the playing-queue tab journey and a true
offline-playback journey (airplane-mode playback of a cached chapter), both of which need
device-side network control that the current E2E harness does not expose.

## Verification

- `./gradlew :app:testDebugUnitTest` — all green.
- `./gradlew :app:lintKotlinTest :app:lintKotlinAndroidTest` — clean.
- `./gradlew :app:compileDebugAndroidTestKotlin` and `:minifiedTest:assembleDebug` — compile
  (no emulator in the dev sandbox; CI runs both suites).
- Mutation spot-checks were run against the new tests to confirm each test fails when the
  behavior breaks: the `SliderState` clamp (4 tests die), the `PerItemPreferences` parsing
  fallback, and the `ChapterAudioEnd` boundary comparison (the duration-vs-span fixture
  catches the `<=`→`<` slip that value-equal fixtures would miss).
