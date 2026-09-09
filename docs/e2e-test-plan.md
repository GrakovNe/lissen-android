# E2E Test Plan (minified build)

## Scope and target

Black-box UI tests against the **R8-minified** app (`org.grakovne.lissen.minified`), driven by
UI Automator from the `minifiedTest` module. The point is not feature coverage (the debug
`androidTest` suite has 147 tests for that) but proving the *shipped artifact* works: R8 must not
break reflection, kotlinx.serialization, Hilt DI, ExoPlayer/media3 callbacks, or `viewId`
lookups used by the UI.

## Test environment

- **QA server, not the public demo.** The suite must run against our QA instance with a pinned
  fixture dataset. The current fallbacks in `LoginFlowE2ETest` (`https://demo.lissenapp.org`,
  user `demo`) must be removed: arguments become mandatory, the suite fails fast if they are
  missing, so tests can never silently run against demo.
- Configuration arrives as instrumentation arguments, sourced from masked GitLab CI/CD
  variables (to be provisioned with the QA stand data):

  | Instrumentation arg | GitLab variable | Purpose |
  |---|---|---|
  | `-e e2eHost` | `E2E_HOST` | QA server URL |
  | `-e e2eUsername` | `E2E_USERNAME` | test account |
  | `-e e2ePassword` | `E2E_PASSWORD` | test account password |

  ```
  adb shell am instrument -w \
    -e e2eHost "$E2E_HOST" -e e2eUsername "$E2E_USERNAME" -e e2ePassword "$E2E_PASSWORD" \
    org.grakovne.lissen.minifiedtest/androidx.test.runner.AndroidJUnitRunner
  ```

- Runner: headless emulator `pixel_6`, system image `android-34` (existing `verify` job),
  animations off, `ANDROID_SERIAL` pinned, AVD recreated with `-wipe-data` per pipeline.
- Every test starts from `clearAppData` + cold start, so tests are order-independent.

## QA server fixtures (required, data itself TBD)

- Account `e2e` with access to a pinned library:
  - `Fixture Series One` — 3+ short chapters (~1 min, Vorbis/Opus) for playback tests;
  - `Fixture Series Two` — many chapters / paging, for library navigation;
  - a title unique enough that a search query returns exactly it;
  - `Fixture Large Chapter` — one ~500 MB chapter for download tests;
  - `Fixture Broken` — a series with intentionally invalid files: truncated audio, zero-byte
    file, wrong mime/extension, garbage bytes served under an audio name.
- QA stand API access for assertions that the UI cannot see (sync state, bookmarks on server):
  `E2E_API_TOKEN` GitLab variable + a small helper in the test module that queries the server
  over HTTP directly.
- Dataset is stable between pipelines; if the QA stand allows, a reset hook runs before the suite.

## Suite layout

One test class per area, 2-5 tests each. **Invariant: every pipeline runs the whole suite** -
no smoke/nightly split, no tag filtering, nothing quarantined to a schedule. Expect the `verify`
job to grow to roughly 30-40 min (~45 tests with cold starts plus sync/large-file scenarios);
that is the accepted cost. When adding tests keep the runtime in mind, but never move a test
out of the main run.

### 1. Login and session (exists, extend)

| # | Test | Expected | Status |
|---|---|---|---|
| 1.1 | Login screen shows host/username/password/login fields | fields visible | done |
| 1.2 | Valid credentials navigate to library | `libraryScreen` visible | done |
| 1.3 | Wrong password stays on login | `loginButton` still visible | done |
| 1.4 | Empty credentials stay on login, no crash | `loginButton` visible | planned |
| 1.5 | Unreachable host shows error, no crash | error state visible, process alive | planned |
| 1.6 | Session survives app restart | login, force-stop, relaunch -> library without login form | planned |

1.6 is the R8 sentinel for token serialization and DI.

### 2. Library

| # | Test | Expected |
|---|---|---|
| 2.1 | After login the fixture series is visible | card with fixture title |
| 2.2 | Open series -> chapter list | chapters of fixture series |
| 2.3 | Search for unique fixture title | exactly the fixture series |
| 2.4 | Scroll long list, no crash | last chapter reachable |

### 3. Playback (highest R8 risk: media3, ExoPlayer, MediaSession)

| # | Test | Expected |
|---|---|---|
| 3.1 | Play first chapter | player visible, position advances |
| 3.2 | Pause / resume | position freezes / advances again |
| 3.3 | Seek forward | position jumps |
| 3.4 | Background playback | home, notification exists, position still advances |
| 3.5 | Resume position after restart | player shows previous position |

### 4. Settings

| # | Test | Expected |
|---|---|---|
| 4.1 | Open settings, change theme | applied immediately |
| 4.2 | Theme survives restart | cold start uses chosen theme |
| 4.3 | Equalizer screen opens | no crash (media3 effect classes under R8) |
| 4.4 | Playback speed change applies to playback | player shows new speed, pitch/audio follow |
| 4.5 | Sleep timer fires | playback stops after the shortest selectable delay |
| 4.6 | Every settings sub-screen opens and backs out | no crash on any screen (smoke walk) |

### 5. Bookmarks

| # | Test | Expected |
|---|---|---|
| 5.1 | Create bookmark during playback | bookmark appears in the list with current timestamp |
| 5.2 | Tap bookmark | player seeks to the bookmarked position |
| 5.3 | Delete bookmark | gone from the list, survives restart |
| 5.4 | Bookmark reaches the server | QA API (`E2E_API_TOKEN`) shows the bookmark for the chapter |

### 6. Widgets and app shortcuts

Widget/shortcut plumbing is registered via manifest and reflection-friendly metadata - R8
and resource shrinking can silently break both.

| # | Test | Expected |
|---|---|---|
| 6.1 | Widget providers registered | `dumpsys appwidget` lists both Lissen receivers (under the `.minified` package) |
| 6.2 | Bind widget via Android 14 `cmd appwidget bind` | host view renders, logcat clean (no launcher UI needed) |
| 6.3 | Playback state reaches the bound widget | start playback, widget shows playing state |
| 6.4 | App shortcuts listed | `pm get-app-shortcuts <launcher>` returns the continue-playback shortcut |
| 6.5 | Launch via shortcut intent | lands on the player with the last book |

### 7. Offline downloads and local playback

| # | Test | Expected |
|---|---|---|
| 7.1 | Download a short chapter | completes, shows "downloaded" state |
| 7.2 | Play downloaded chapter with network off | plays from local storage (airplane mode via `cmd connectivity airplane-mode enable`) |
| 7.3 | Cancel download mid-flight | partial state cleaned, chapter back to "download" |
| 7.4 | Downloaded chapter survives app restart | still listed and playable offline |

### 8. Sync (batch)

Assertions combine UI and direct QA stand API checks (`E2E_API_TOKEN`).

| # | Test | Expected |
|---|---|---|
| 8.1 | Listen 30 s -> position on server | QA API reports progress > 0 for the chapter |
| 8.2 | Fresh install resumes server position | clear data, login, player offers server position |
| 8.3 | Server-side library change syncs | fixture added via API appears after pull/sync |
| 8.4 | Offline changes flush on reconnect | progress made in airplane mode reaches server after reconnect |
| 8.5 | Repeated sync is idempotent | two syncs -> no duplicate series/chapters |
| 8.6 | Token refresh / re-auth mid-session | expire token on server side -> app re-logins transparently |

### 9. Large files and invalid input

| # | Test | Expected |
|---|---|---|
| 9.1 | Download `Fixture Large Chapter` (~500 MB) | completes within timeout, plays |
| 9.2 | Cancel large download at ~50% | storage reclaimed (`df` delta), no orphan files |
| 9.3 | Play truncated audio file | recoverable error shown, app alive |
| 9.4 | Play zero-byte / garbage-under-audio-mime file | error handled, no crash, other chapters still playable |
| 9.5 | Storage pressure | fill emulator storage near full -> download fails with a clear error, no crash |

### 10. Robustness

| # | Test | Expected |
|---|---|---|
| 10.1 | Process death during playback (`am kill`) | restart resumes the chapter at the last position |
| 10.2 | Network cut during playback, then restore | error handled, playback resumable (`cmd connectivity airplane-mode`) |
| 10.3 | R8 guard: scan logcat after suite | no `ClassNotFoundException` / `NoSuchMethodError` / `Proguard`-related failures |

## Extension candidates (proposed, not yet scheduled)

| Area | Test | Why |
|---|---|---|
| Podcasts | browse a podcast, play an episode | `PodcastAudiobookshelfChannel` is a second content type with its own converters - zero e2e coverage |
| Recent listening | continue-listening shelf shows the last played book | `RecentListeningResponseConverter` path, also backs the shortcut |
| Media session | `adb shell media dispatch play_pause / fast-forward` controls playback | notification/headset path without UI taps |
| Locale | switch system locale to `cs-CZ` and `zh-CN`, cold start | translations + Compose resources under R8, cheap to run |
| Upgrade path | install previous release, then the minified build over it | Room `Migrations` and session survival across update - classic R8/Room breakage; requires signing both APKs with the CI keystore |
| Storage cleanup | delete a downloaded book frees disk | `ContentCachingManager` removal path, complements 7.x/9.2 |
| Caching notification | bulk caching shows progress and completes | `ContentCachingNotificationService` |

## Failure diagnostics

On any failed test capture and upload as CI artifact:

- `adb exec-out screencap` screenshot,
- last 500 lines of `logcat -d`,
- UI Automator hierarchy dump (`uiautomator dump`).

## CI integration and acceptance

- The full suite runs in the existing `verify` job after the debug instrumented suite
  (already wired). Every pipeline, every test - no lane filtering.
- Pass arguments from GitLab variables (section above); variables are masked, protected.
- A test is "adopted" only after 3 consecutive green pipelines; flaky-first tests run as
  `retry: 1` for one iteration, then must be fixed or deleted.
- Remove demo fallbacks from `LoginFlowE2ETest` as part of phase 1.5/1.6 work.

## Order of work

1. Provision QA stand data + fixtures (incl. large/broken) -> GitLab variables; drop demo fallbacks.
2. Phase 1 remainder (1.4-1.6).
3. Phase 3 playback (highest risk, biggest value).
4. Phases 2, 4, 5 (bookmarks), 6 (widgets/shortcuts).
5. Phase 7 offline downloads, then phase 8 sync batch (needs `E2E_API_TOKEN` helper).
6. Phase 9 large/invalid files, phase 10 robustness.
7. Diagnostics/artifacts polish; freeze suite size.
