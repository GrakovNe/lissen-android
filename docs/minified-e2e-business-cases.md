# Minified E2E — user business cases

Executable plan for the `minifiedTest` suite: black-box user journeys against the
R8-minified app (`org.grakovne.lissen.minified`), ordered simple → complex.
This document supersedes `e2e-test-plan.md` for day-to-day execution: the QA stand with
custom fixtures is not provisioned, so the suite runs against the public demo server,
same as the debug `androidTest` suite. The QA-stand ideas in `e2e-test-plan.md`
(broken fixtures, 500 MB files, admin API) stay as backlog until the stand exists.

## Ground rules

- Runner: `androidx.test.uiautomator` (`uiAutomator {}` DSL) + `uiautomator-shell`
  (`Shell.application.clearAppData`, `Shell.wifi`). No Hilt, no Compose test APIs.
- Elements are found by `viewIdResourceName` (Compose `testTag`s are exposed as resource
  ids via `TestTagsAsResourceId`) and by visible text / content description.
- Target: `org.grakovne.lissen.minified`; server/credentials via instrumentation args
  with demo fallbacks (`e2eHost`/`e2eUsername`/`e2ePassword`, default
  `https://demo.lissenapp.org`, `demo`/`demo`).
- Every test starts from `clearAppData` + cold start (order-independent).
- Emulator locale must be `en-US` (`settings put system system_locales en-US`).
- Playback readiness signal: resource id `chapterList` (the bottom bar is a placeholder
  with dead clicks until playback is ready). Budget up to 120 s.
- Demo server state is shared and mutable (progress/bookmarks persist across runs):
  assert state-independently (e.g. "Continue listening is non-empty"), never exact
  server-side values written by earlier runs.
- Process discipline: write one test → make it green → commit → next. Never batch.

## Phase 1 — Login and session (simple)

| ID | Case | Assertions |
|---|---|---|
| 1.1 | Login screen fields visible | `hostInput`, `usernameInput`, `passwordInput`, `loginButton` — **done** |
| 1.2 | Valid credentials → library | `libraryScreen` — **done** |
| 1.3 | Wrong password stays on login | `loginButton` still visible — **done** |
| 1.4 | Empty credentials → stays on login, no crash | `loginButton` visible, process alive |
| 1.5 | Settings openable from login screen | `loginSettingsButton` → settings screen visible → back → login form intact |
| 1.6 | Session survives app restart | login, force-stop, relaunch → `libraryScreen` without login form |
| 1.7 | Disconnect from server | Settings → Connection → "Disconnect from the server" → confirm → login screen |

## Phase 2 — Library browsing

| ID | Case | Assertions |
|---|---|---|
| 2.1 | Grid shows books after login | `libraryGrid`, ≥1 `bookItem_*` |
| 2.2 | Search finds a known book | tap search (`a11y_search`), type title into `librarySearchField`, matching `bookItem_*` visible; clear (`a11y_clear`) → grid restored |
| 2.3 | Quick settings sheet | tap `a11y_menu` → "Grouping" (Disabled / By Series / By Author) and "Sort by" options visible; pick "By Series" → `seriesItem_*` appear; revert to Disabled |
| 2.4 | Hide finished / Downloaded only toggles | both rows present in the sheet; toggling "Downloaded only" shows offline-empty or filtered grid, no crash; revert |
| 2.5 | Open a book → player | tap `bookItem_*` → `playerScreen` with title |
| 2.6 | Continue listening shelf | after a playback (runs inside 3.x) shelf "Continue listening" non-empty |

## Phase 3 — Playback (core user story)

| ID | Case | Assertions |
|---|---|---|
| 3.1 | Play a book | open book → wait `chapterList` → `a11y_pause` visible → position text advances |
| 3.2 | Pause / resume | `a11y_play` ↔ `a11y_pause` toggle; position frozen while paused |
| 3.3 | Seek forward / back | `a11y_fast_forward_seconds` / `a11y_rewind_seconds` change the position text |
| 3.4 | Next / previous chapter | `a11y_next_track` changes `playerChapterNumber`; `a11y_previous_track` returns |
| 3.5 | Background playback | press home, wait ~10 s, reopen app → position advanced; Lissen notification visible in the shade (best-effort) |
| 3.6 | Resume after app restart | kill + relaunch → "Continue listening" shows the book; reopening resumes near the saved position |

## Phase 4 — Player tabs and overlays

| ID | Case | Assertions |
|---|---|---|
| 4.1 | Chapters tab | tap "Chapters" → `chapterList` with entries; tap another chapter → it becomes current |
| 4.2 | Speed tab | tap "Speed" → "Playback speed" dialog; pick 1.5x → tab/selection reflects it; playback continues |
| 4.3 | Sleep timer set / cancel | tap "Timer" → "Sleep Timer" dialog (10/15/30/60 min, "When the chapter ends"); pick 15 → countdown shown; reopen → "Disable Timer" cancels. Firing is not asserted (min preset 10 min) |
| 4.4 | Info screen | `playerInfoButton` → title/author/description visible → back to player |
| 4.5 | Bookmarks | bookmarks icon in player top bar → "Bookmarks" screen; "Create bookmark" → entry with timestamp appears; tap → position seeks to it; delete → gone. Prereq: the icon has `contentDescription = null` and no tag — add `.testTag("playerBookmarksButton")` to the icon button in `PlayerScreen.kt` |

## Phase 5 — Settings (every screen, every control)

Walk pattern per sub-screen: open from Settings, assert the control, change it, assert the
effect (where observable in UI), restore default, back out.

### 5.x Appearance

| ID | Control | Assertions |
|---|---|---|
| 5.1 | Color scheme (System/Light/Dark/Black) | pick Dark → option shows selected state and survives app restart; visual darkness checked via background-color assertion only if the assertions API cooperates |
| 5.2 | Material You colors | toggle flips without crash; state persists |
| 5.3 | Library sorting (Title/Author/Date created/Date updated) | pick an option → library grid order changes or option state persists |

### 5.y Playback

| ID | Control | Assertions |
|---|---|---|
| 5.4 | Seek settings: rewind/forward interval | change "Rewind interval" → player rewind button label changes to new value |
| 5.5 | Force software decoding | toggle persists |
| 5.6 | Boosted volume | open picker, pick option, persists |
| 5.7 | Playback on notification (Lower volume/Pause) | pick option, persists |
| 5.8 | Equalizer | open → bands + "Restore default" visible; toggle Enabled/Disabled; restore defaults; back |
| 5.9 | Timer settings: Fade out, Reduce volume when playback stops, Fade duration | toggles flip, duration picker opens, states persist |
| 5.10 | Default sleep timer | pick "When the chapter ends" → selection shown; revert to Disabled |

### 5.z Downloads

| ID | Control | Assertions |
|---|---|---|
| 5.11 | Download automatically (global toggle) | flip + revert, persists |
| 5.12 | Use for automatic downloads (WiFi only / WiFi or cellular) | pick, persists |
| 5.13 | Download automatically — library types (Books/Podcasts) | toggle a type, persists |
| 5.14 | Delay automatic download | toggle flips, persists |
| 5.15 | Storage location | opens picker/confirmation dialog; cancel leaves state unchanged |
| 5.16 | Manage saved content → Downloads screen | opens ("No saved content yet" when empty; populated in 6.x) |

### 5.w Connection

| ID | Control | Assertions |
|---|---|---|
| 5.17 | Server info block | "Connected as", "Connection type", "Server Version" visible |
| 5.18 | Disable SSL verification | toggle flips, persists |
| 5.19 | Change User Agent + Restore Default | edit value → persists; restore → default returns |
| 5.20 | Custom Headers: add + delete | add Key/Value row → visible; delete → gone |
| 5.21 | Local network server address: add + delete | add URL + SSID row → visible; delete → gone |
| 5.22 | Client certificate screen | opens, empty state "No client certificate selected" |

### 5.v Advanced

| ID | Control | Assertions |
|---|---|---|
| 5.23 | Activity Logging | toggle flips, persists |
| 5.24 | Crash reporting | toggle flips, persists |
| 5.25 | Backup & Restore screen | "Export configuration" / "Import configuration" rows visible |
| 5.26 | Export logs | taps into share sheet or "No logs available"; no crash |
| 5.27 | Clear thumbnail cache | confirmation dialog → "Clear" → success toast |

## Phase 6 — Offline (complex)

| ID | Case | Assertions |
|---|---|---|
| 6.1 | Download a chapter | player → Downloads tab → start download of the smallest chapter → "Available offline" (`a11y_available_offline`) state appears |
| 6.2 | Play offline | `Shell.wifi.disable()` → downloaded chapter plays (position advances) |
| 6.3 | Offline library filter | "Downloaded only" in library quick settings shows the downloaded book |
| 6.4 | Downloads screen lists content | Settings → Downloads → "Manage saved content" shows the book; remove it → back to "No saved content yet" |
| 6.5 | Reconnect | `Shell.wifi.enable()` → online browsing works again; app alive |

## Phase 7 — Robustness

| ID | Case | Assertions |
|---|---|---|
| 7.1 | Process kill during playback | `Shell.process` kill mid-play → relaunch → resumes at saved position |
| 7.2 | Network cut during playback | wifi off mid-play → app alive, error recoverable; wifi on → resume playback |
| 7.3 | Rotation during playback | rotate → player controls present, playback continues |

## Excluded as decorative

App artwork/illustrations, GitHub link row, license footer, album artwork pane (visual only),
toasts' exact wording, share-sheet internals (only "opens/closes without crash").

## Known constraints / risks

- **Demo library contents are not pinned**: book choice for playback/download is discovered
  at runtime (first book / smallest chapter). Assertions must not hardcode titles.
- **Two rows share the text "Download automatically"** on the Downloads settings screen
  (global toggle `settings_download_automatically_title` and library-type picker
  `download_settings_library_type_title`). Locate them by surrounding description text or
  by index, never by text alone.
- **Podcasts tab appears only if the server has podcasts** — the demo account may not;
  podcast assertions are conditional (skip when the tab is absent).
- **Sleep timer firing** (≥10 min) and **default-timer end-of-chapter** are not asserted —
  only selection UI.
- **Storage location switching** would wipe downloads — dialog is opened and cancelled only.
- **Playback isolation**: if a second playback entry in one test process fails to reach
  ready (observed in the debug suite), merge steps into one test rather than splitting.
- **Airplane vs wifi**: emulator network is wifi; `Shell.wifi.disable()` is sufficient to
  prove offline behaviour against the remote demo server.

## Definition of done

- Every row of Phases 1–7 is implemented, green, and committed.
- Coverage matrix: every interactive element and every app setting listed in Phase 5 maps to
  at least one passing case (decorative exclusions aside).
- Full suite passes 3 consecutive local runs; CI job for `minifiedTest` stays green.
