# Minified E2E — coverage map (gaps beyond happy paths)

Snapshot of what the `minifiedTest` suite covers today and what it does not, derived from
the test sources on `main` (33 tests) cross-checked against app code (testTags, content
descriptions, `strings.xml`). Companion of `minified-e2e-business-cases.md`; the phase IDs
below refer to that document.

Legend: ✅ covered · ⬜ gap, feasible on the CI emulator · 🚫 blocked (reason given).

## 1. Inventory — what is covered today

| Suite | Tests | Cases |
|---|---|---|
| `LoginFlowE2ETest` | 7 | 1.1–1.7 (fields, valid login, wrong password, empty credentials, settings from login, session survives restart, disconnect) |
| `LibraryFlowE2ETest` | 5 | 2.1 grid, 2.2 search hit + clear + back, 2.3 grouping by series (apply/revert), 2.4 downloaded-only toggle, 2.5 open book |
| `PlaybackFlowE2ETest` | 3 | 3.1 play/pause, 3.2 seek ±(position via `dumpsys media_session`), 3.3 next/previous chapter |
| `PlayerTabsFlowE2ETest` | 5 | 4.1 chapter select, 4.2 speed 1.5, 4.3 timer set (no cancel), 4.4 downloads tab options, 4.5 bookmarks sheet opens |
| `SettingsFlowE2ETest` | 6 | top-level categories; Appearance/Playback/Downloads/Advanced open + controls present; Color scheme dialog reflects |
| `RobustnessFlowE2ETest` | 1 | 7.3 rotation during playback |
| `ShortcutFlowE2eTest` | 3 | registration after playback, drawer shortcut, intent launch |
| `WidgetFlowE2eTest` | 3 | placeholder tap opens app, state widget controls, cover widget controls |

Everything covered so far is the **direct scenario**: the control is present, the primary
action works, the UI comes back. Nothing covers invalid input, empty results, cancellation,
persistence across restart, cross-screen contracts, or secondary branches.

## 2. Gaps — feasible, to be added

Feasibility was verified against the code: every selector below exists as a `testTag`,
content description, or visible string on `main`.

### Login robustness

| ID | Case | Plan | Status |
|---|---|---|---|
| G01 | Unreachable host | host = `http://10.255.255.1` (non-routable), valid user/pass → app stays on `loginButton`, process alive (`pidof` non-empty) | ✅ |
| G02 | Malformed host | host = `not a url` → stays on login, no crash, fields still editable | ✅ |

### Library

| ID | Case | Plan | Status |
|---|---|---|---|
| G03 | Search with no results | query = random gibberish → `waitUntilAbsent(bookItem_*)`, no crash; clear + back → grid restored | ✅ |
| G04 | Search with special characters | query `«%&"()` → no crash, still on `librarySearchField` | ✅ |
| G05 | Sort by Author | quick settings → "Sort by" → "Author" → grid shows books; sheet reopens with the choice; revert to default | ✅ |
| G06 | Grouping by Author | quick settings → Grouping → "By Author" → `authorItem_*` tags appear; revert | ✅ |
| G07 | Hide finished | toggle in quick settings → no crash, grid (or empty) restored; revert | ✅ |
| G08 | Continue listening shelf (2.6) | play a book → back to library → text "Continue listening" + a `bookItem_*` under it | ✅ |

### Player / playback

| ID | Case | Plan | Status |
|---|---|---|---|
| G09 | Background playback (3.4) | play → `pressHome` → relaunch → `media_session` still `PLAYING(3)` | ✅ |
| G10 | Player restored after process kill (7.1-lite) | play → `am force-stop` → relaunch → `playerScreen` shows same `playerChapterNumber` (chapter only, no position — clock is frozen) | ✅ |
| G11 | Sleep timer cancel (4.3b) | set 15 min → reopen Timer tab → "Disable Timer" → "15 minutes" gone | ✅ |
| G12 | Info sheet (4.4) | `playerInfoButton` → sheet shows "Author" or "Duration" row → dismiss → player intact | ✅ |
| G13 | Speed round trip | 1.5x → back to 1.0x → tab shows 1.00x | ✅ |
| G14 | Playback notification | play → open shade → notification with pause action → tap → `PAUSED(2)` | 🚫 stretch (kept out: Media3 notification actions via shade are flaky headless) (Media3 notification actions via shade are flaky headless) |

### Settings — cross-screen contracts and persistence

| ID | Case | Plan | Status |
|---|---|---|---|
| G15 | Server info block (5.17) | Settings → Connection → "Connected as", "Connection type", "Server Version" visible | ✅ |
| G16 | User agent edit + restore (5.19) | Connection → "Change User Agent" → type marker → dismiss → reopen → marker; "Restore default" → default returns | ✅ |
| G17 | Custom headers add + delete (5.20) | Connection → Custom Headers → FAB → fill Key/Value → back → reopen → value persists → delete (desc "Delete from cache") → gone | ✅ |
| G18 | Client certificate empty state (5.22) | Connection → "Client certificate" → "No client certificate selected" | ✅ |
| G19 | Backup & Restore rows (5.25) | Advanced → "Backup & Restore" → "Export configuration" + "Import configuration" | ✅ |
| G20 | Clear thumbnail cache (5.27) | Advanced → row → confirmation dialog → "Clear" → sheet closes (toast not observable via uiautomator on the headless emulator) | ✅ |
| G21 | Export logs (5.26) | Advanced → "Export logs" → share sheet opens or "No logs available"; no crash | ✅ |
| G22 | Equalizer screen (5.8) | Playback → "Equalizer" → band sliders (desc `… hertz band`) + "Restore default" → back | ✅ |
| G23 | Timer settings sub-screen (5.9) | Playback → "Timer settings" → "Fade out" row opens the seconds-or-off sheet; the 15 s preset reaches the row | ✅ |
| G24 | Default sleep timer (5.10) | Playback → "Default sleep timer while playing" → "When the chapter ends" → reflected → revert "Disabled" | ✅ |
| G25 | Seek interval contract (5.4) | Playback → "Seek settings" → set rewind interval to a new value → player rewind button reads "Rewind N seconds" → revert | ✅ |
| G26 | Color scheme survives restart (5.1b) | set Black → force-stop → relaunch → Appearance shows "Black" → revert to System | ✅ |
| G27 | Download network policy (5.12) | Downloads → "Use for automatic downloads" → pick "WiFi or cellular network" → row reflects → revert "WiFi only" | 🚫 row disabled until automatic downloads are on, and downloads never complete on the emulator |

## 3. Blocked — recorded, not testable here

| Case | Why |
|---|---|
| Phase 6 (download → offline → saved content) | downloads stall on the headless emulator (4% forever); `Shell.wifi` offline playback has nothing downloaded to play |
| 7.1 resume at saved position | position clock frozen; G10 asserts the observable half (same chapter) |
| 7.2 network cut during playback | same clock problem + flaky emulator link |
| Toggle on/off state assertions (Material You, SSL bypass, autodownload…) | toggles expose no `checkable`/`checked` node; only row presence is assertable |
| Boosted volume | continuous slider, no discrete selection |
| Bookmark create/delete (4.5+) | mutates the shared demo server irreversibly; no delete affordance |
| Storage location switch (5.15) | wipes downloads; dialog open/cancel only |
| Sleep timer firing, end-of-chapter default timer | ≥10 min wall clock |
| Local network URL delete (5.21) | delete button has `contentDescription = null` — no selector; add-only would leave junk config |

## 4. Review notes (self-review of this map)

- **Selector grounding.** `authorItem_*`, `bookItem_*`, `librarySearchField`,
  `playerInfoButton`, `playerChapterNumber` are real testTags; "Delete from cache",
  "Disable Timer", "Restore default", "Connected as", "When the chapter ends",
  "No client certificate selected", "Export configuration", "Thumbnail cache cleared",
  "No logs available", "WiFi or cellular network", "Rewind %d seconds" are real strings
  in `strings.xml` on `main`. No planned test depends on a selector that does not exist.
- **No clock dependency** in any planned case (frozen-clock rule respected): G09/G10 use
  `state=PLAYING(3)` and chapter number, never position-over-time.
- **No destructive demo-server mutation**: G16/G17 revert what they change; G20 clears a
  local cache only; bookmark creation stays excluded.
- **Order-independence**: every case starts from `freshApp`/`loggedInApp` (clearAppData),
  except G10/G26 which kill and relaunch the app on purpose within one test.
- **Risk call-outs.** G14 (notification shade) is the flakiest — kept as stretch, drop if
  it cannot hold 5/5. G05 asserts persistence of the sort choice rather than exact grid
  order (library contents are not pinned). G21 accepts either branch (share sheet or
  "No logs available") — the contract is "no crash, one of the two".
- **Duplication check**: none of G01–G27 repeats an existing assertion; G05/G06 extend
  the 2.3 pattern to the sort control and the author grouping, which 2.3 does not touch.
