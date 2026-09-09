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
  - a title unique enough that a search query returns exactly it.
- Dataset is stable between pipelines; if the QA stand allows, a reset hook runs before the suite.

## Suite layout

One test class per area, 2-5 tests each. Budget: every test costs a cold start (~10-15 s),
keep the whole suite under ~30 tests / ~10 min.

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

### 5. Robustness

| # | Test | Expected |
|---|---|---|
| 5.1 | Rotation during playback | player survives, keeps playing |
| 5.2 | Network cut during playback, then restore | error handled, playback resumable |
| 5.3 | R8 guard: scan logcat after suite | no `ClassNotFoundException` / `NoSuchMethodError` / `Proguard`-related failures |

## Failure diagnostics

On any failed test capture and upload as CI artifact:

- `adb exec-out screencap` screenshot,
- last 500 lines of `logcat -d`,
- UI Automator hierarchy dump (`uiautomator dump`).

## CI integration and acceptance

- Suite runs in the existing `verify` job after the debug instrumented suite (already wired).
- Pass arguments from GitLab variables (section above); variables are masked, protected.
- A test is "adopted" only after 3 consecutive green pipelines; flaky-first tests run as
  `retry: 1` for one iteration, then must be fixed or deleted.
- Remove demo fallbacks from `LoginFlowE2ETest` as part of phase 1.5/1.6 work.

## Order of work

1. Provision QA stand data -> GitLab variables; drop demo fallbacks.
2. Phase 1 remainder (1.4-1.6).
3. Phase 3 playback (highest risk, biggest value).
4. Phases 2, 4, 5.
5. Diagnostics/artifacts polish; freeze suite size.
