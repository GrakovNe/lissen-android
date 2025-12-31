---
trigger: always_on
---

# Project Context and Guidelines

## About Project

This is an offline first Android client app for the [audiobookshelf](https://github.com/advplyr/audiobookshelf) server.

## Feature Requirements (Offline first behavior)

- As the app is an offline-first app, assume that the server is not always reachable.
- Playback progress should be saved in local database first **immediately**.
- The app should watch the network changes, such as connecting to a new wifi network, or lan network, or disconnecting from a network and connecting to a new network, disconnecting from wifi and connecting to celular network etc, and try to ping or reachout the audiobook server to check whether the server is reachable.
- If the server is reachable, the app should sync the local progress to the server and pull the latest progress updates from the server to the local database, it should merge the updates from both.
- If the server is reachable, and some chapters of any audiobook is download, i.e. available offline, then offline track should be given priority for playback.
- If the offline track is deleted / cleared while the book is being played, it should fallback to the online URL if the server is reachable, else it should pause the playback, and make sure to correctly store the last
- The app must be fully functional for downloaded content when offline.

## Ensure Stability

- Ensure null-safety when converting data (e.g., check for division by zero in percentage calculations).
- Changes must be verified by building the app and ensuring logic holds (e.g., uninstall/reinstall for clean state tests).

## Overall Functionality

- When the app loads, it should load the offline available book content immediately, then in the background reach out to the server (if the server is reachable) and fetch the full list of the books, continue listening section books and update the UI seemlessly.
- The app should cache all the book's metadata in local database to optimise the app load time, Only the chapters / audio tracks should not be cached automatically / by default. Chapters should be downloaded and cached on demand by the user, using the download chapters/book functionality.
- When the app loads, if the server is not reachable, it shouldn't show long loading screen, trying to fetch the books from the server, it should load the book's list from the local database, however it should only show the books whos' chapters are downloaded and available offline can be played.
- When the server becomes reachable, it should update the books list, as now all the books can be played from local cahce or online from the server.
- When the network is switched, the app should trigger checking whether the server is still reachable or not, if not reachable, it should update the UI to only show offline available ready to play books.

## General Guidelines and Standards

- **Colors**: Must be referenced from `Color.kt` / `Theme.kt`. Do not use raw hex values (e.g., `0xFF...`) in Composables.
- **Dimensions**: Must use `Spacing.kt` (e.g., `Spacing.md`, `Spacing.lg`) for padding, margins, and layout dimensions.
- **Design System**: Adhere effectively to the spacing system and color palette defined in the project.
- **ABSOLUTELY NO** hardcoded user-facing strings in UI code. All strings must be extracted to `strings.xml` and accessed via `stringResource`.
- Use `associateBy` or proper indexing for collection lookups (O(1)) instead of nested loops (O(N^2)) when synchronizing data.
- Avoid expensive operations on the main thread.
- No code duplication, keep the code clean and easy to maintain.
