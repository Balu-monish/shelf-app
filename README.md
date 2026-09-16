# Shelf

An Android app that's a mix of Kindle (EPUB reading) and Audible (M4B audiobook
listening) in one library, with a similar UI to both. Books are imported from local
device storage, pulled on-demand from user-configurable Google Drive folders (see
[Google Drive sync](#7-google-drive-sync) below), or preloaded as bundled samples on
first launch (`data/samples/SampleContentSeeder.kt`) — the only other network access is
a plain HTTPS download for one of those samples' audiobook, sourced from a public
archive.org link rather than bundled in the APK.

This document records **why** the app is built the way it is, and what every
file does, so a future session doesn't have to re-derive it by reading code
cold.

> **Build & run status:** builds successfully, and has been run and tested
> end-to-end on a real emulator — local import, the audiobook player
> (including a real threading crash that got fixed, see below), the
> EPUB reader, and Google Drive sync (including the mid-sync crash that got
> fixed, see below) have all been confirmed working. Reader-preference
> persistence, highlights/notes, preloaded samples, configurable Drive
> folders, and the Settings screen are this round's additions and haven't
> been through a real device test yet the way the rest of the app has.

---

## 1. Key decisions and why

| Decision | Why |
|---|---|
| **Local files only, via Storage Access Framework** — no backend, no cloud sync | Simplest architecture that satisfies "I have EPUB/M4B files"; avoids building and hosting a server. `ACTION_OPEN_DOCUMENT` + persisted URI permission means the app never copies large audiobook files into its own storage — it just remembers a permission to the original file. |
| **EPUB and audiobook progress tracked independently** (no Whispersync-style position sync) | Explicit user decision in planning — real added complexity (matching editions, mapping text position to audio timestamp) for a "nice to have." The data model doesn't block adding it later. |
| **Single Gradle module, package-by-feature** (`data`, `player`, `reader`, `ui/*`) | Right-sized for a solo-built app. A multi-module split would be premature structure for the current size. |
| **EPUB metadata (title/author/cover) extracted with a hand-rolled parser**, not Readium, during import (Phase 1) | Readium's exact API shifts between minor versions and couldn't be compile-checked in this environment. Import only needs title/author/cover, which is just reading `META-INF/container.xml` → the OPF file → a manifest item — doable with only `java.util.zip` + `javax.xml.parsers` (stdlib, zero version risk). Readium is still used later for actual EPUB *rendering*, where there's no way around it. |
| **M4B metadata via `android.media.MediaMetadataRetriever`**, not Media3, during import | Same reasoning: the platform's `MediaMetadataRetriever` already parses MP4/M4B 'ilst' tags and hands back embedded cover art directly (`embeddedPicture`) with a long-stable, well-known API. Media3 is pulled in later, only where it's actually needed (playback). |
| **Audio playback: Media3 `ExoPlayer` + `MediaSessionService` + `MediaController`** | Standard, current (Media3 1.11) Google-recommended architecture for background-capable audio playback with lock-screen/notification controls. |
| **Chapter list sourced from Media3 1.11's native MP4/M4B chapter-atom parsing** (`androidx.media3.extractor.metadata.Chapter`), not a hand-written MP4 box parser | Media3 added this in August 2026, verified by reading the actual `Chapter.java`/`Mp4Extractor.java` source on `github.com/androidx/media` at the `1.11.0` tag rather than guessing. Far more reliable than parsing `chap`/Nero chapter atoms by hand. |
| **EPUB rendering: Readium Kotlin Toolkit's `EpubNavigatorFragment`**, bridged into Compose via `AndroidView` + `FragmentContainerView` | Readium has no Compose-native EPUB navigator yet (only a Fragment/View-based one, plus newer WebView-based navigators for other formats). This is the one piece of unavoidable, genuinely delicate platform-interop code in the app — see "Known risks." |
| **Progress storage: `Locator` JSON for EPUB, raw position-ms for audio** | Readium's `Locator` (title/href/percentage/CFI-like location) is the correct unit for "where in an EPUB," and it already serializes to/from JSON. Audio position is just milliseconds — no need for anything fancier. |
| **Bookmarks: one shared `Bookmark` table**, `position` stored as a format-specific string (ms for audio, `Locator` JSON for EPUB) | Avoids two near-identical tables. The app-layer code (not the DB) knows how to interpret `position` based on which screen is using it. |
| **Toolchain: AGP 9.4.0 / Kotlin 2.4.20 / KSP 2.3.12 / Gradle 9.7.0**, bumped mid-build in Phase 3 | Discovered while adding the Readium dependency that Readium 3.4.0 (current, verified against Maven Central) is itself built with Kotlin 2.4.20/AGP 9.3.1/Gradle 9.7.0. Consuming a library built with a much newer Kotlin than the app's own compiler risks metadata-version errors, so the whole project's toolchain was upgraded to current verified-stable versions to remove that risk, rather than leaving a latent landmine. This is why the `android { kotlinOptions {} }` block is gone from `app/build.gradle.kts` — that DSL doesn't exist anymore at this Kotlin version; `jvmTarget` is now set via a top-level `kotlin { compilerOptions {} }` block instead. |
| **`compileSdk`/`targetSdk` 37** | Matches what Readium 3.4.0 itself was compiled against, to avoid an SDK-level mismatch warning. |
| **Core library desugaring enabled** (`isCoreLibraryDesugaringEnabled = true` + `desugar_jdk_libs:2.1.5`) | AGP's AAR metadata check refused to build without it — Readium's three modules (`shared`/`streamer`/`navigator`) all declare it as a requirement. This matches what Readium's own build does internally; not something this app would otherwise need at `minSdk` 26. |
| **No `org.jetbrains.kotlin.android` plugin applied** | AGP 9.0+ has Kotlin support built in, and applying the separate plugin on top of it is a hard error ("no longer required... remove the plugin"), not just a warning — hit this on the first real Gradle sync and removed it from both `build.gradle.kts` files and the version catalog. `org.jetbrains.kotlin.plugin.compose` and `com.google.devtools.ksp` are unaffected and still applied normally; the `kotlin { compilerOptions {} }` block for `jvmTarget` is also unaffected — it was already the current (non-deprecated) syntax. |
| **`minSdk` 26** (Android 8.0) | Needed for adaptive launcher icons without extra legacy-icon resources, and is a reasonable modern floor for a new app. |

### How each phase built on the last

1. **Phase 0 — scaffolding.** Gradle/Compose/Hilt/Room skeleton, navigation graph with placeholder screens, Material 3 theme (light/dark/dynamic color).
2. **Phase 1 — import + library.** SAF file picker, the two metadata extractors above, Room persistence, a real (if plain) library grid and detail screen.
3. **Phase 2 — audiobook player.** `PlaybackService`, `PlayerController`, full player UI (seek, skip, speed, sleep timer, chapters).
4. **Phase 3 — EPUB reader.** Readium integration for opening + rendering, TOC, font-size/theme controls, progress autosave. This is also where the toolchain got upgraded (see table above).
5. **Phase 4 — polish.** Library search/filter/sort, a "Continue" row, per-book progress bars on covers, and bookmarks for both formats.

---

## 2. Project structure

```
app/src/main/java/com/ebooksplayer/shelf/
├── MainActivity.kt            entry point (FragmentActivity, hosts Compose)
├── ShelfApplication.kt        Hilt entry point; also kicks off one-time sample seeding
├── data/
│   ├── db/                    Room: entities, DAOs, database class
│   ├── importer/               SAF import pipeline + metadata extractors
│   ├── download/               plain-URL content download (non-Drive stubs)
│   ├── drive/                  Google Drive sync
│   ├── samples/                 one-time bundled-sample-content seeding
│   ├── settings/                app-wide + reader preference persistence (SharedPreferences)
│   └── repository/            thin repositories wrapping DAOs/importer for ViewModels
├── di/                        Hilt modules (things that need a Provides, not just @Inject)
├── player/                    audiobook playback (Media3), independent of Compose
└── ui/
    ├── theme/                 Material 3 color scheme + typography
    ├── navigation/            NavHost + route definitions
    ├── library/                library grid screen + its ViewModel
    ├── detail/                 book detail screen + its ViewModel
    ├── player/                 "now playing" screen + its ViewModel
    ├── reader/                 EPUB reader screen + its ViewModel + the Fragment bridge
    └── settings/                Settings screen + its ViewModel
```

Everything else (`build.gradle.kts`, `settings.gradle.kts`, `gradle/`,
`gradle.properties`) is standard Gradle project plumbing.

---

## 3. File-by-file reference

### Root / build config

- **`settings.gradle.kts`** — declares the `app` module and Gradle's dependency repositories (`google()`, `mavenCentral()`).
- **`build.gradle.kts`** (root) — declares the plugins used, without applying them (`apply false`); each is actually applied in `app/build.gradle.kts`.
- **`gradle/libs.versions.toml`** — the version catalog. Every dependency's group/artifact/version lives here, referenced from `app/build.gradle.kts` as `libs.xxx`. Centralizes version bumps.
- **`app/build.gradle.kts`** — the app module's build config: `applicationId`, SDK levels, the `kotlin { compilerOptions {} }` block (jvmTarget 17), and the full dependency list.
- **`gradle.properties`** — Gradle/Kotlin/AndroidX flags, including `ksp.useKSP2=true` (needed to pair KSP with current Kotlin).
- **`gradle/wrapper/gradle-wrapper.properties`** — pins the Gradle version (9.7.0). The wrapper *jar* itself isn't present (couldn't be generated without Gradle installed) — Android Studio creates it on first sync.
- **`app/proguard-rules.pro`** — empty; minification is off in the release build type for now (`isMinifyEnabled = false`), so nothing to configure yet.

### Manifest & resources

- **`app/src/main/AndroidManifest.xml`** — declares: the app's theme/icon, `MainActivity` as the launcher, the `PlaybackService` (a `MediaSessionService`, `foregroundServiceType="mediaPlayback"`), and the permissions that requires (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`), plus `INTERNET` (added for Drive sync — the only network access the app has). No storage permissions are needed — SAF handles that per-file.
- **`res/values/themes.xml`**, **`ic_launcher_background.xml`**, **`res/drawable/ic_launcher_foreground.xml`**, **`res/mipmap-anydpi-v26/ic_launcher*.xml`** — adaptive launcher icon (a simple book/page glyph) and the base (non-Compose) app theme Android needs before Compose takes over.
- **`res/values/strings.xml`** — just the app name, "Shelf" (a placeholder — rename freely).

### `data/db` — Room persistence

- **`entity/Book.kt`** — one row per known book: title, author, cover file path, `format` (`EPUB`/`M4B`), `fileUri` (nullable — a SAF `content://` URI for local imports, a local `file://` copy once a Drive/URL book is downloaded, or `null` for a not-yet-downloaded stub), import date, duration (audio) or page count (ebook, currently unused), `driveFileId` (null for local imports; set for anything Drive sync has seen), `driveSizeBytes` (shown on the Download button), `isDownloaded` (always true for local imports; false for a stub until its content is fetched), `series`/`seriesIndex` (EPUB-only, from Calibre metadata), and `downloadUrl` (a plain HTTPS source for a stub that isn't from Drive — currently only the preloaded sample audiobook — fetched with a bare GET, no OAuth token; mutually exclusive with `driveFileId` in practice).
- **`entity/EbookProgress.kt`** — one row per ebook: the current Readium `Locator` (as JSON), a 0–1 percentage, last-read timestamp.
- **`entity/AudiobookProgress.kt`** — one row per audiobook: chapter index, position in ms, playback speed, last-played timestamp.
- **`entity/Bookmark.kt`** — user-saved bookmarks for either format; `position` is a ms-string (audio) or `Locator` JSON (ebook), interpreted by whichever screen reads it.
- **`entity/Collection.kt`** / **`entity/BookCollectionCrossRef.kt`** — user-defined collections (`id`, `name`, `createdAt`) and a many-to-many join table (composite `bookId`+`collectionId` key, cascade-delete on either side) — a book can be in more than one collection, matching how Kindle's own "Collections" feature works.
- **`entity/Highlight.kt`** — a user text highlight in an EPUB: `bookId` (FK, cascade-delete), `locatorJson` (a Readium `Locator` covering the selected range — Readium doesn't persist decorations itself, so this is re-applied as a `Decoration` every time the book is opened), `colorArgb` (one of two fixed colors — plain highlight vs. "has a note", not a full color picker), an optional `note`, `createdAt`. Deliberately a separate table from `Bookmark`: a highlight is a text *range* with a color/note, not a single jump-to point, so it doesn't fit `Bookmark`'s shape.
- **`dao/BookDao.kt`** — CRUD + `observeAll()` (a `Flow`, so the library screen updates live) + `updateCoverUri` (used because the cover file is written *after* the book row is first inserted, once the new row's id is known) + `existsWithDriveFileId` (Drive sync's dedup check) + `update` (full-row update, used to hydrate a stub after download).
- **`dao/ProgressDao.kt`** — get/observe progress for one book, plus `observeAll*Progress()` (added in Phase 4, used to compute per-book progress bars across the whole library in one query each rather than one query per book).
- **`dao/BookmarkDao.kt`** — CRUD for bookmarks, scoped by book.
- **`dao/CollectionDao.kt`** — `observeAll()` collections, `insert()`, add/remove a book↔collection cross-ref row, `observeCollectionIdsForBook`/`observeBookIdsInCollection` (the latter drives the library screen's collection filter).
- **`dao/HighlightDao.kt`** — CRUD + `observeForBook(bookId)`, ordered newest-first.
- **`AppDatabase.kt`** — the Room `@Database`, lists all entities/DAOs, and a `Converters` class teaching Room to store the `BookFormat` enum as a string column. Now at **version 6** (5 → 6 added the `highlights` table; see the running version-bump note under [section 7](#7-google-drive-sync)) — every schema change so far has used `fallbackToDestructiveMigration()`, so each one has reset the local library; still an accepted pre-release trade-off, not yet a real migration path.

### `data/importer` — turning a picked file into a library entry

- **`ExtractedMetadata.kt`** — the common result shape (`title`, `author`, `coverBytes`, `durationMs`, `series`, `seriesIndex`) both extractors below produce.
- **`EpubMetadataExtractor.kt`** — opens the EPUB as a zip (`ZipInputStream`), reads `META-INF/container.xml` to find the OPF file's path, parses the OPF's `<metadata>`/`<manifest>` for title/creator/cover image, then reads the cover image's bytes out of the same zip. Three short passes over the zip stream (container.xml → OPF → cover image), each via `contentResolver.openInputStream(uri)`. Also scans the OPF's `<meta>` tags for Calibre's `calibre:series`/`calibre:series_index` convention — **EPUB-only**; `M4bMetadataExtractor` never sets series, since M4B has no equivalent standard tag. Whether a given EPUB has this data at all depends on whether Calibre (or something following its convention) tagged it in the first place.
- **`M4bMetadataExtractor.kt`** — wraps `android.media.MediaMetadataRetriever` to pull title/author/duration/`embeddedPicture` (cover art bytes) straight out of the M4B's tags.
- **`CoverStorage.kt`** — writes a book's cover bytes to `filesDir/covers/<bookId>.jpg` and returns a `file://` URI for Room/Coil to use.
- **`BookImporter.kt`** — the orchestrator: takes a persistable read permission on the picked `Uri` (skipped for non-`content://` URIs, i.e. Drive-downloaded local files), sniffs the format from the filename extension, calls the matching extractor, inserts the `Book` row (optionally tagged with a `driveFileId`), then saves+attaches the cover. Returns a sealed `ImportResult` (`Success` / `UnsupportedFormat` / `Failure`) so the UI can show a specific error. Used identically by local SAF import and Drive sync — Drive sync just hands it a `file://` URI instead of a `content://` one.

### `data/repository` — the thin layer ViewModels actually talk to

- **`BookRepository.kt`** — wraps `BookDao` + `BookImporter`: `observeBooks()`, `observeBook(id)`, `getBook(id)`, `importBook(uri, driveFileId)`, `hasBookWithDriveFileId(id)`, `insertDriveStub(...)`, `insertDownloadableStub(title, author, format, downloadUrl, coverUri)` (a stub sourced from a plain HTTPS URL rather than Drive — used by the sample audiobook), `updateBook(book)`.
- **`ProgressRepository.kt`** — wraps `ProgressDao` for both progress types, including the Phase-4 `observeAll*Progress()` additions used for library-wide progress bars.
- **`BookmarkRepository.kt`** — wraps `BookmarkDao`: observe/add/delete.
- **`CollectionRepository.kt`** — wraps `CollectionDao`: same thin-passthrough shape as the others.
- **`HighlightRepository.kt`** — wraps `HighlightDao`: `observeForBook(bookId)`, `addHighlight(bookId, locatorJson, colorArgb, note)`, `deleteHighlight(highlight)`.

### `data/settings`

- **`AppSettingsRepository.kt`** — two independent app-wide preferences, both plain `SharedPreferences` (not DataStore — small, primitive-shaped values, not worth the extra dependency): the dark-mode `ThemeMode` (unchanged from before), and `driveFolders: StateFlow<List<DriveFolderMapping>>` — the user-editable list of `(folder name, format)` pairs Drive sync scans, serialized as a small JSON array, seeded with the original hardcoded three (`DEFAULT_DRIVE_FOLDERS`) on first read so nothing changes until the user edits it from the Settings screen. `MainActivity` reads `themeMode` directly (to wrap the whole `NavHost` in `ShelfTheme`); `SettingsScreen`/`SettingsViewModel` read and write both.
- **`ReaderPreferencesRepository.kt`** — persists the reader's font size/family, line height, and theme (the subset of Readium's `EpubPreferences` the app's UI exposes) as a global default, `SharedPreferences`-backed like the above. This is what makes reading preferences apply to whichever book is opened and survive app restarts, replacing the old per-session `remember { mutableStateOf(...) }` in `ReaderScreen`. Both the reader's own "Aa" dialog and the Settings screen's "Reading defaults" section read/write this same `StateFlow<EpubPreferences>`, so a change in either place is immediately reflected in the other.

### `data/download`

- **`ContentDownloader.kt`** — downloads a file from a plain, publicly-accessible HTTPS URL with a bare anonymous GET (no `Authorization` header, unlike `DriveApi.downloadFile`). Used for `Book.downloadUrl`-sourced stubs (currently just the preloaded sample audiobook); reuses the `OkHttpClient` singleton `DriveModule` already provides.

### `data/samples`

- **`SampleContentSeeder.kt`** — runs once per install (gated by a `SharedPreferences` flag, same pattern as the settings repositories), triggered from `ShelfApplication.onCreate()`. Copies the bundled `assets/sample/1984.epub` (316 KB) to internal storage and imports it through the normal `BookRepository.importBook` path, so it's fully readable immediately after first launch. The sample audiobook (`Sample/AudioBooks/MashiAndOtherStories_librivox.m4b`, 106 MB) is deliberately not bundled — per an explicit call to keep the app bundle small rather than ship 106 MB of audio nobody asked for — instead only its metadata is preloaded via `BookRepository.insertDownloadableStub(...)` with `downloadUrl` pointing at its public archive.org link, downloadable on demand exactly like a Drive stub.

### `data/drive` — Google Drive sync (see [section 7](#7-google-drive-sync) for the full picture)

- **`DriveEntry.kt`** — a Drive `files.list` result item (`id`, `name`, `isFolder`, `sizeBytes`, `thumbnailLink`).
- **`DriveApi.kt`** — thin OkHttp wrapper around the Drive v3 REST API: find a folder by name, list a folder's immediate children (paginated), download a file's bytes. Deliberately not the generated `google-api-services-drive` client — direct REST is the current recommended approach for Android and keeps the dependency light. `listChildren` requests `thumbnailLink` too (see the cover-thumbnail note below).
- **`DriveAuthManager.kt`** — wraps Play Services' `AuthorizationClient` to get a Drive-scoped access token, handling the case where a consent screen must be shown (exposes the `IntentSender` as a `StateFlow` for the UI to launch, resumes via `onConsentResult`). Deliberately *not* Credential Manager sign-in — this app never needs to know who the user is, only that it has a valid token, so the simpler `AuthorizationClient`-only flow was chosen (see section 7).
- **`DriveSyncRepository.kt`** — two entry points. `sync()`: for each `DriveFolderMapping` in `AppSettingsRepository.driveFolders` (user-editable from Settings, defaulting to the original three), finds the folder, walks it recursively for matching-format files, and inserts a name-only stub `Book` row for anything not already known (`BookDao.existsWithDriveFileId`) — no file content is fetched, though Drive's `thumbnailLink` (if one happens to exist) is stored as the stub's `coverUri` for free. `downloadBook(bookId)`: fetches one stub's actual content — via `DriveApi.downloadFile` with an OAuth token if `driveFileId` is set, or via the auth-free `ContentDownloader` if `downloadUrl` is set instead (the sample-audiobook case) — to `filesDir/drive_books/`, runs it through the same extractors `BookImporter` uses, and updates the row in place with real metadata + `isDownloaded = true`. Both download paths converge on the same metadata-extraction/hydration logic since it's format-driven, not source-driven.

**Cover thumbnails for not-yet-downloaded stubs — an honest caveat.** `Book.coverUri` for a stub is set from Drive's `thumbnailLink` when present, and the library grid / Book Detail render it exactly like any other cover, with a small cloud-download icon as a corner *overlay* badge (not a full replacement of the cover area) to mark it as not downloaded. In practice, expect this to rarely if ever show a real cover: Google Drive only auto-generates thumbnails for file types it knows how to preview (images, PDFs, Google Docs, video, etc.) — EPUB and M4B aren't in that set. This was still worth building because it's free (one extra field on an API call already being made) and correct/future-proof, but it is **not** backed by any partial-file-download cover-extraction attempt — doing that would undermine the entire reason sync doesn't download full files in the first place.

### `di` — Hilt modules

Only classes that *can't* just use `@Inject constructor` (because they wrap a third-party type, or need a `@Provides` factory) get a module here; everything else (repositories, most ViewModels) is constructor-injected directly.

- **`AppModule.kt`** — provides `ContentResolver` from the app `Context`.
- **`DatabaseModule.kt`** — builds the Room `AppDatabase` and provides its five DAOs. Uses `fallbackToDestructiveMigration()` — see section 7 for why.
- **`ReaderModule.kt`** — provides Readium's `HttpClient` (`DefaultHttpClient`), `AssetRetriever`, and `PublicationOpener` — the three objects needed to open an EPUB as a Readium `Publication`.
- **`DriveModule.kt`** — provides a singleton `OkHttpClient` for `DriveApi`.

### `player` — audiobook playback (no Compose dependency)

- **`PlaybackService.kt`** — a `MediaSessionService` hosting one `ExoPlayer` + one `MediaSession`, with 15s-back/30s-forward seek increments configured on the player itself. This is what keeps audio playing when the app is backgrounded and what puts transport controls on the lock screen/notification shade.
- **`PlayerChapter.kt`** — a small app-level chapter model (`index`, `title`, `startMs`, `endMs`) that `PlayerController` maps Media3's `Chapter` metadata entries onto.
- **`PlayerController.kt`** — the singleton the rest of the app actually talks to. Connects a `MediaController` to `PlaybackService`, exposes playback state as `StateFlow`s (`isPlaying`, `positionMs`, `durationMs`, `speed`, `chapters`, sleep-timer remaining), and offers the transport functions (`playBook`, `togglePlayPause`, `seekTo`, `skipForward/Back`, `setSpeed`, `seekToChapter`, sleep timer start/cancel). `extractChapters()` is the one function reading Media3's `@UnstableApi` `Chapter` metadata — verified against Media3's actual GitHub source rather than guessed, since it's a very recent (Aug 2026) addition.

### `ui/theme`

- **`Color.kt`** — the app's palette (amber/ink/cream).
- **`Type.kt`** — typography (serif titles, evoking a book).
- **`Theme.kt`** — `ShelfTheme` composable: light/dark color schemes, with Android 12+ dynamic color if available. Takes a `ThemeMode` (`LIGHT`/`DARK`/`SYSTEM`, from `AppSettingsRepository`) rather than a raw boolean — `SYSTEM` still follows the device setting via `isSystemInDarkTheme()`, `LIGHT`/`DARK` force it. This is the app-wide dark mode toggle; the reader's own Light/Sepia/Dark theme (section 3, `ui/reader`) is separate and unaffected by it.

### `ui/navigation`

- **`Destinations.kt`** — the four routes (`Library`, `BookDetail`, `Reader`, `Player`) and the `bookId` nav-argument key they all share.
- **`ShelfNavHost.kt`** — wires those routes to their screens via `NavHost`, extracting `bookId` from the back-stack entry where needed.

### `ui/library` — the home screen

- **`LibraryViewModel.kt`** — combines the book list with both progress tables (to compute a 0–1 progress fraction per book), the user's search/filter/sort state, and the collection filter into one `LibraryUiState`. Sort modes: recently opened / recently added / title / author / series (sorts by `series ?: title`, then `seriesIndex`, then title — a flat sort, not a grouped/sectioned view). **Every sort mode has `isDownloaded` layered on top as the primary key** (`compareByDescending { it.book.isDownloaded }.then(innerComparator)`) — a downloaded book always sorts ahead of a not-yet-downloaded stub, regardless of which mode is active. The collection filter (`selectedCollectionId`, `null` = no filter) is combined via `flatMapLatest` against `CollectionRepository.observeBookIdsInCollection`, re-subscribing whenever the selected collection changes. Also computes the "Continue" list: the 10 most-recently-opened books across both formats. Handles the local import flow (delegates to `BookRepository`, surfaces errors), the Drive sync flow (delegates to `DriveSyncRepository`/`DriveAuthManager`), and `collections`/`selectCollection`/`createCollection` (passthrough to `CollectionRepository`). No longer owns theme state — that moved to the Settings screen talking to `AppSettingsRepository` directly.
- **`LibraryScreen.kt`** — search field, format filter chips (All/Books/Audiobooks), a second scrollable chip row for collections (All + one per collection + a "+ New" chip opening a small create dialog), a sort menu, a "Sync from Drive" icon button (spinner while syncing), a gear icon navigating to the Settings screen (replaced the old inline theme-mode dropdown), the "Continue" horizontal row, and the main cover grid — each cover showing a format badge, a small cloud-download *corner badge* (not a full replacement of the cover) for not-yet-downloaded stubs, and (if the book has been opened before) a thin progress bar along its bottom edge.

### `ui/detail`

- **`BookDetailViewModel.kt`** — observes one `Book` by id as a `Flow` (via `SavedStateHandle` for the id, which Hilt's nav integration populates from the route argument automatically) rather than a one-shot fetch, so the screen updates live when a Drive download finishes. Owns the download flow (`download()`, `isDownloading`, `downloadError`) via `DriveSyncRepository`, and the collection-assignment flow (`collections`, `bookCollectionIds`, `toggleCollection(id)`) via `CollectionRepository`.
- **`BookDetailScreen.kt`** — cover (with the same not-downloaded corner badge as the library grid), title/author, either a single action button ("Read" for EPUB, "Listen" for M4B — a book is only ever one format) if downloaded or a "Download" button (showing size, if known) with a progress spinner if it's a not-yet-downloaded Drive stub, and a "Collections" row of toggleable chips (one per existing collection; a hint to create one from the library screen if there aren't any yet — collection *creation* only happens there, not here).

### `ui/player` — "Now Playing"

- **`PlayerViewModel.kt`** — on init, loads the `Book`, loads any saved `AudiobookProgress`, and tells `PlayerController` to start playback from there. Combines all of `PlayerController`'s state flows plus the loaded book into one `PlayerUiState`. Autosaves progress every 5s while active and once more in `onCleared()`. Also owns bookmarks for this book (`BookmarkRepository`, position stored as a ms-string).
- **`PlayerScreen.kt`** — cover, title/author, current chapter label, a draggable seek bar, skip/play/pause controls, a speed menu, a sleep-timer menu, and two bottom sheets: chapters (jump to any chapter) and bookmarks (add one at the current position, jump to or delete a saved one).

### `ui/reader` — the EPUB reader

- **`ReaderViewModel.kt`** — opens the book's file as a Readium `Publication` (`AssetRetriever.retrieve` → `PublicationOpener.open`), restores the last saved `Locator` from Room, and closes the `Publication` when the screen is left. Also owns bookmarks for this book (position stored as `Locator` JSON, remembers the last-seen `Locator` so "bookmark this page" has something to save), highlights for this book (`highlights: StateFlow<List<Highlight>>` from `HighlightRepository`, `addHighlight(locator, note)` picking one of two fixed colors depending on whether a note is attached, `deleteHighlight`), and reading display preferences (`preferences: StateFlow<EpubPreferences>` sourced straight from `ReaderPreferencesRepository`, `updatePreferences` writes through it — no local view-model-only copy).
- **`EpubNavigatorHost.kt`** — **the one genuinely delicate file in the app**, and where this round's text-selection work landed. Readium's EPUB renderer (`EpubNavigatorFragment`) is a `Fragment`, not a composable, so this bridges it into Compose: hosts a `FragmentContainerView` via `AndroidView`, registers a custom `FragmentFactory` (Readium's fragments can't be constructed directly — their constructors are `internal`), attaches/detaches the fragment as the host composable enters/leaves composition, forwards preference changes (font size, theme) via `submitPreferences`, and debounces the navigator's `currentLocator` flow into progress-save callbacks. `MainActivity` had to become a `FragmentActivity` (not plain `ComponentActivity`) for this to have a `FragmentManager` to attach to.

  **Selection/highlight integration** — verified against Readium 3.4.0's actual GitHub source (`SelectableNavigator.kt`, `DecorableNavigator.kt`, the `Configuration` class inside `EpubNavigatorFragment.kt`, `Locator.kt`) rather than guessed, same practice as the Media3 chapter-extraction work. A custom `android.view.ActionMode.Callback` is built and handed to `EpubNavigatorFragment.Configuration(selectionActionModeCallback = ...)` *before* the fragment exists (the fragment reference is filled in via a closed-over `var` right after `commitNow` creates it, since Kotlin closures see the latest value of a captured local `var`). Providing this callback means owning the whole text-selection toolbar, so **Copy is added back explicitly** via `ClipboardManager` alongside four new actions (Highlight, Note, Dictionary, Search Web) — selecting text shouldn't lose a capability users already had. On tap, `fragment.currentSelection()` (suspend, from `SelectableNavigator`) gives a `Selection(locator, rect)`; `locator.text.highlight` is the actual selected string, no WebView JS extraction needed. Existing highlights are rendered by mapping the `highlights` list to `Decoration(id, locator, Decoration.Style.Highlight(tint))` and calling `applyDecorations(..., "highlights")` (from `DecorableNavigator`) whenever the list changes; a `DecorableNavigator.Listener` registered via `addDecorationListener` handles tapping an existing highlight back in the UI. Callback/listener closures read the latest `highlights` list and UI callbacks via `rememberUpdatedState`, so they never go stale across recomposition without needing to restart the underlying `DisposableEffect`.
- **`ReadingPreferencesControls.kt`** — the font-size/line-spacing/font-family/theme controls, extracted out of `ReaderScreen.kt` into a standalone composable so both the reader's own "Aa" dialog and the Settings screen's "Reading defaults" section can share one implementation editing the same persisted `EpubPreferences` — no copy-pasted UI.
- **`ReaderScreen.kt`** — top bar with contents/bookmarks/highlights/display-settings actions, a table-of-contents bottom sheet, a bookmarks bottom sheet, a highlights bottom sheet (jump to or delete a saved highlight), and a display-settings dialog built on `ReadingPreferencesControls` that live-updates the navigator via `submitPreferences` *and* writes through to `ReaderPreferencesRepository` — so changes now persist across books and app restarts, not just for the current session. Selecting text surfaces a note-entry dialog (for the Note action) and routes Dictionary/Search Web to OS-level intents: `Intent.ACTION_PROCESS_TEXT` (offered via `Intent.createChooser`, so it reaches whatever dictionary/translate apps are installed) for Dictionary, and `Intent.ACTION_VIEW` against a Google search URL (opens the device's default browser) for Search Web — deliberately no custom dictionary API/dependency for either.

### `ui/settings`

- **`SettingsViewModel.kt`** — a thin aggregator over three things that already had their own persistence: `themeMode`/`setThemeMode` (passthrough to `AppSettingsRepository`), `readingPreferences`/`setReadingPreferences` (passthrough to `ReaderPreferencesRepository` — the same `StateFlow<EpubPreferences>` the reader's own "Aa" dialog reads/writes), and `driveFolders`/`addDriveFolder`/`removeDriveFolder` (passthrough to `AppSettingsRepository.driveFolders`, list add/remove implemented as whole-list replace since it's a short user-edited list, not worth per-row DB operations).
- **`SettingsScreen.kt`** — reached from a gear icon on the library top bar (replacing the old inline theme dropdown). Three sections in a scrollable column: **Theme** (Light/Dark/System chips), **Reading defaults** (`ReadingPreferencesControls`, shared with the reader's own dialog), and **Drive sync folders** (the current list of folder-name/format rows with a delete icon each, plus a name field + Books/Audiobooks chips + add button for a new one) — this is what makes "point the app at a folder in Drive" user-editable instead of the original three hardcoded names, which remain the default.

---

## 4. Known risks / what to check first if the build fails

Roughly in order of how likely they are to need a fix, and why:

1. **`EpubNavigatorHost.kt`'s Fragment-in-Compose lifecycle.** The general pattern (AndroidView + FragmentContainerView + a FragmentManager transaction) is well-established, but its exact behavior across rotation, back-navigation, and process death couldn't be exercised here. If the reader screen crashes or shows a blank page, start here.
2. **Exact Readium 3.4.0 API surface beyond what was directly verified.** The `AssetRetriever`/`PublicationOpener`/`EpubNavigatorFactory`/`EpubPreferences`/`Theme` calls, and (added this round) the `SelectableNavigator`/`DecorableNavigator`/`Decoration`/`ActionMode.Callback` selection-and-highlight APIs in `EpubNavigatorHost.kt`, were checked against Readium's actual GitHub source at the `3.4.0` tag — but Readium's toolkit is large, and something adjacent (e.g. an import path) could still be slightly off.
3. **First-build dependency resolution.** This is a heavier, more recent toolchain (AGP 9.4.0, Kotlin 2.4.20) than most tutorials assume; Android Studio may want to download new SDK platforms or suggest further patch-version bumps. Accept those.
4. **The gradle wrapper jar is missing** (noted above) — Android Studio generates it on first open; if it doesn't, run `gradle wrapper --gradle-version 9.7.0` once a system Gradle is available.

Everything under `player/` (Media3) was checked against the real `androidx/media` source at the `1.11.0` tag for the one non-obvious part (chapter extraction); the rest of the Media3 usage is long-stable API. `data/importer` uses only JDK/Android framework APIs with no version risk.

### Bugs actually found (and fixed) once the app started running

Compile errors on first build (missing `AAR` desugaring requirement, a bad
icon import, an `Icons.Filled.X` extension-property alias used without its
receiver, `TopAppBar`'s experimental opt-in missing in one file, an invalid
`item` import) were all one-line fixes — see the git history / conversation
that produced this build rather than repeating them here.

The one substantive **runtime** bug: **opening an audiobook crashed the app**
with `IllegalStateException: MediaController method is called from a wrong
thread`. Root cause: `PlayerController`'s `CoroutineScope` had no dispatcher
specified in its context, so `launch {}` defaulted to `Dispatchers.Default`
(a background thread pool) — but Media3's `MediaController` requires every
call to happen on the thread that created it (the main thread). Fixed by
adding `Dispatchers.Main.immediate` to that scope's context. Also hardened
while fixing this: `PlayerController` now has a `CoroutineExceptionHandler`
and surfaces playback failures as a `playbackError` `StateFlow` (shown as a
snackbar in `PlayerScreen`) instead of letting an uncaught exception crash
the whole app — this is why the crash showed up as a readable on-screen
message on the second attempt instead of a force-close.

The same class of bug showed up again in Drive sync: **the app closed partway
through syncing** (after successfully importing several books/audiobooks).
Root causes, both fixed together:
1. `OkHttpClient()` was built with no custom timeouts, so OkHttp's default
   10-second read timeout applied — nowhere near enough for a
   several-hundred-MB-to-multi-GB audiobook download. `DriveModule` now
   builds the client with a 10-minute read timeout.
2. `DriveSyncRepository.sync()` had no error handling around any of the
   per-file network/disk work, so a single failed download (timeout,
   connection reset, anything) threw an uncaught exception straight through
   `LibraryViewModel`'s `viewModelScope.launch`, crashing the app — the exact
   same shape of bug as the player crash above, just in a different file.
   Fixed the same way: every per-file step is now wrapped in `runCatching`
   (one failed file just counts as `failed` and sync moves on to the next),
   the whole `sync()` body has an outer `try/catch` returning a `Failure`
   outcome instead of throwing, and `LibraryViewModel.syncFromDrive()` has
   its own `try/catch/finally` as a last line of defense so `isSyncing` can
   never get stuck `true` either.

**Lesson reinforced:** any `viewModelScope.launch` (or similar unscoped
coroutine) that does real I/O needs explicit error handling from the start —
this is now the third time in this project the same "uncaught exception in a
coroutine crashes the whole app" bug has shown up (Media3 controller calls,
then here). Worth treating as a standing checklist item for any future
network/IO-touching ViewModel code, not just something to patch reactively.

---

## 5. Building it

1. Open the `player/` folder in a recent Android Studio (needs to support AGP 9.x / Kotlin 2.4.x).
2. Let it sync — accept any prompted SDK Platform 37 download and wrapper regeneration.
3. Run on a device or emulator running Android 8.0 (API 26) or newer.
4. Tap **+** on the library screen and pick a real `.epub` or `.m4b` file to import and test with.

## 7. Google Drive sync

A "Sync from Drive" button (top bar of the library screen) lists new books/audiobooks
from a user-editable list of Google Drive folders (Settings → Drive sync folders),
defaulting to the same three folders `sync-to-drive.ps1` (repo root, see that script for
the upload side) pushes local files into:

| Drive folder | Local source (via the script) | Imported as |
|---|---|---|
| `Dedrm books` | `De-DRM/` | EPUB |
| `drm free books` | `DRM-Free/` | EPUB |
| `de drm audio books` | `DE-DRM Audio/` | M4B |

Each is walked recursively (the script mirrors nested author/book subfolders as-is), so
sync finds files at any depth under those three root folders.

**Sync lists, it doesn't download.** This was an explicit ask: pulling every matching
file's full content during sync would mean multi-GB downloads just to show a library
listing. So `DriveSyncRepository.sync()` only calls Drive's `files.list` and inserts a
lightweight "stub" `Book` row per new file — `isDownloaded = false`, `fileUri = null`,
title = the filename (no `.epub`/`.m4b` extension), `driveSizeBytes` from Drive's own
file-size field. **This means a stub's title is genuinely just the filename** — no
author, no cover — because both EPUB and M4B metadata extraction need the actual file
bytes, which sync deliberately doesn't fetch. The library grid shows these with a
cloud-download icon instead of a cover so they're visually distinct from "downloaded but
no cover art found." Opening one goes to Book Detail, which shows a **Download** button
(with the size, e.g. "Download (450 MB)") instead of Read/Listen.

**Download is a separate, explicit, per-book action** (`DriveSyncRepository.downloadBook`,
triggered from `BookDetailViewModel.download()`): fetches that one file's content,
runs it through the *same* `EpubMetadataExtractor`/`M4bMetadataExtractor`/`CoverStorage`
that local SAF imports use, and updates the stub row in place with the real
title/author/cover/duration plus `fileUri` and `isDownloaded = true`. Book Detail
observes the row as a `Flow` (not a one-shot fetch), so it flips from "Download" to
"Read"/"Listen" the moment this finishes, with no need to leave and re-enter the screen.

**Auth.** Uses Play Services' `AuthorizationClient` (`data/drive/DriveAuthManager.kt`)
requesting the `drive.readonly` scope — not Credential Manager sign-in, since the app
never needs to know *who* the user is, only that it holds a valid token. This was verified
against current (2026) Android guidance and against the actual `play-services-auth`
library bytecode (no public source repo for Play Services, so the AAR's `.class` files
were inspected directly for exact method signatures — `AuthorizationRequest.builder()`,
`.setRequestedScopes(List<Scope>)`, `AuthorizationResult.hasResolution()`/`.accessToken`/
`.pendingIntent`, `AuthorizationClient.getAuthorizationResultFromIntent(Intent)` — rather
than guessed from summarized docs, learning from earlier mistakes in this project).

**Drive access.** Direct REST calls via OkHttp (`data/drive/DriveApi.kt`) to the Drive v3
API (`files.list`, `files/{id}?alt=media`), not the generated `google-api-services-drive`
Java client — that client is heavy and not Android-friendly; direct REST is Google's
current recommended approach.

**Dedup.** `Book.driveFileId` (nullable) records which Drive file a book (stub or
downloaded) came from; sync skips anything already present, whether or not it's been
downloaded yet — re-running sync never re-lists or re-downloads existing rows. This
required a Room schema version bump (now **4**: `isDownloaded`/`driveSizeBytes` were added
in the 2 → 3 bump on top of the 1 → 2 bump `driveFileId` itself needed, and the
`series`/`seriesIndex` columns used for Sort → Series added the 3 → 4 bump) using
`fallbackToDestructiveMigration()`
rather than a real `Migration` — acceptable at this pre-release stage (local library data
gets wiped once on upgrade; re-importing is trivial since the source files still exist
locally and/or on Drive). **Known gap:** a book already imported locally via the file
picker has no `driveFileId`, so if it also exists in the synced Drive folder it can show
up as a second, separate stub entry — not solved (would need fuzzy title-matching, not
worth it for personal use).

**Downloaded files are real local copies**, not references — unlike SAF imports, which
just keep a permission grant to the original file wherever the user put it. A download
lands in `filesDir/drive_books/` and `Book.fileUri` is then set to that local copy, which
both Media3 and Readium handle the same way they handle any other local file URI. Until
downloaded, `fileUri` is `null` — this is why it had to become a nullable field, with a
guard added at the two places that open it (`PlayerViewModel`, `ReaderViewModel`), even
though in practice they're only reachable via Book Detail's Read/Listen button, which
only appears once `isDownloaded` is true.

### One-time setup this feature needs (only the user can do this — like the rclone `config` step)

Before Drive sync can be tested, an OAuth client must exist in Google Cloud Console:

1. Create or pick a Cloud project, enable the **Google Drive API** for it.
2. Configure the **OAuth consent screen** (External user type; add your own Google account
   as a **test user** — this skips Google's app-verification review entirely, which is
   fine for personal use, but means anyone *not* listed as a test user would see a warning
   screen).
3. Create an **OAuth 2.0 Client ID** of type **Android**, giving it the app's package name
   (`com.ebooksplayer.shelf`) and the **debug keystore's SHA-1 fingerprint** (get it via
   `./gradlew signingReport` in the `player/` folder). No client ID needs to be embedded in
   app code — Play Services matches the calling app's package + signature against this
   registration automatically.

## 8. Not built yet (explicitly out of scope so far)

- Cross-format "Whispersync"-style position linking between an EPUB and its audiobook edition.
- DRM (Readium supports LCP; this app doesn't wire it up).
- Automated tests.
- A real Room migration for the `driveFileId` column (currently a destructive fallback — fine pre-release, not fine once there's real user data to preserve across upgrades).
