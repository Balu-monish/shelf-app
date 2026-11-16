# Shelf

**One library for your ebooks and audiobooks.** Shelf is an Android app that reads
EPUBs (like Kindle) and plays M4B audiobooks (like Audible), side by side in a
single library. You can import books from local storage, pull them on demand
from Google Drive, or start with the bundled samples.

Built with Kotlin and Jetpack Compose, with Media3 for playback and the Readium
Kotlin Toolkit for EPUB rendering.

---

## Features

### Library
- Cover grid for EPUBs and M4B audiobooks together, with format badges
- A **Continue** row with your 10 most recently opened books
- Per-book progress bars drawn on the covers
- Search, a Books / Audiobooks filter, and sorting by recently opened, recently added, title, author or series (series data is read from Calibre metadata)
- User-defined **collections**; a book can belong to more than one
- Light, dark and system themes, with Material You dynamic color on Android 12+

### EPUB reader
- EPUB rendering via Readium, with a table of contents
- Font size, font family, line height, and Light / Sepia / Dark reading themes, saved across books and restarts
- Bookmarks
- **Highlights and notes**: select text to highlight, annotate, copy, look up in a dictionary, or search the web
- Reading position saved automatically

### Audiobook player
- Background playback with lock-screen and notification controls (Media3 `MediaSessionService`)
- Chapter list read from the M4B's chapter atoms
- 15 s back / 30 s forward skipping, a draggable seek bar, and playback speed control
- Sleep timer
- Bookmarks, plus automatic progress saving

### Google Drive sync
- Scans Drive folders that you configure in Settings
- **Lists without downloading**: sync only adds lightweight placeholder entries, so a large audiobook library doesn't fill your device
- Download one book at a time from its detail page, on demand
- Read-only Drive access (`drive.readonly`)

---

## Screens

| Screen | What it does |
|---|---|
| Library | Grid, search/filter/sort, collections, Drive sync |
| Book detail | Cover, metadata, Read / Listen / Download, collection membership |
| Reader | EPUB reading, TOC, bookmarks, highlights, display settings |
| Player | Now Playing: chapters, speed, sleep timer, bookmarks |
| Settings | Theme, default reading preferences, Drive sync folders |

---

## Tech stack

| Area | Library |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Architecture | MVVM, Hilt for DI, Kotlin Coroutines & Flow |
| Persistence | Room, SharedPreferences |
| Audio | AndroidX Media3 (ExoPlayer, MediaSession) |
| EPUB | Readium Kotlin Toolkit 3.4 |
| Networking | OkHttp (Drive v3 REST API) |
| Auth | Google Play Services `AuthorizationClient` |
| Images | Coil |

**Toolchain:** AGP 9.4 · Kotlin 2.4.20 · KSP 2 · Gradle 9.7 · `compileSdk`/`targetSdk` 37 · `minSdk` 26 (Android 8.0)

---

## Architecture

A single Gradle module, organised by feature:

```
app/src/main/java/com/ebooksplayer/shelf/
├── MainActivity.kt          FragmentActivity hosting Compose
├── ShelfApplication.kt      Hilt entry point; seeds sample content on first launch
├── data/
│   ├── db/                  Room entities, DAOs, database
│   ├── importer/            File import + EPUB / M4B metadata extraction
│   ├── drive/               Google Drive auth, REST client, sync
│   ├── download/            Plain-HTTPS downloads for non-Drive items
│   ├── samples/             First-launch sample content
│   ├── settings/            App and reader preferences
│   └── repository/          Repositories used by the ViewModels
├── di/                      Hilt modules
├── player/                  Media3 playback service + controller
└── ui/
    ├── library/  detail/  reader/  player/  settings/
    ├── navigation/
    └── theme/
```

Some notable design choices:

- **No storage permissions.** Local files are opened through the Storage Access Framework with persisted URI permissions, so large audiobooks stay where they are and are never copied into the app.
- **Lightweight metadata import.** EPUB title, author and cover are read with a small zip + XML parser, and M4B tags with `MediaMetadataRetriever`. Readium and Media3 are only loaded when you actually read or listen.
- **Progress is stored per format.** EPUB positions are saved as Readium `Locator` JSON; audiobook positions are saved in milliseconds.
- **Readium inside Compose.** Readium's `EpubNavigatorFragment` is embedded in Compose with `AndroidView` + `FragmentContainerView` (see `ui/reader/EpubNavigatorHost.kt`), and highlights are drawn as Readium decorations.
- **Drive placeholders.** Synced files are stored as placeholder `Book` rows (`isDownloaded = false`). When a download finishes, the row is filled in using the same metadata extractors as local imports.

---

## Getting started

### Requirements
- Android Studio with support for AGP 9.x / Kotlin 2.4
- Android SDK Platform 37
- A device or emulator running Android 8.0 (API 26) or newer

### Build and run
1. Clone the repository:
   ```bash
   git clone https://github.com/Balu-monish/shelf-app.git
   ```
2. Open the project in Android Studio and let Gradle sync. Accept any prompts to install the SDK or generate the Gradle wrapper.
3. Run the `app` configuration on a device or emulator.
4. Tap **+** in the library to import an `.epub` or `.m4b` file.

> The Gradle wrapper JAR isn't committed. Android Studio generates it on first
> sync, or you can run `gradle wrapper --gradle-version 9.7.0` with a local Gradle install.

### Setting up Google Drive sync (optional)

Drive sync needs an OAuth client registered for your own build:

1. In the [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the **Google Drive API**.
2. Configure the **OAuth consent screen** (External), and add your Google account as a **test user**.
3. Create an **OAuth 2.0 Client ID** of type **Android**, with:
   - package name `com.ebooksplayer.shelf`
   - your signing key's SHA-1 (`./gradlew signingReport`)

You don't need to put a client ID or API key in the code: Play Services matches
your app's package name and signing certificate against that registration.

In the app, go to **Settings → Drive sync folders** to choose which Drive
folders to scan and whether each one holds books or audiobooks. The defaults are
`Books` (EPUB) and `Audiobooks` (M4B). Then tap the sync button in the library.

---

## Roadmap

- [ ] Linking an EPUB to its audiobook edition with shared position sync
- [ ] Proper Room migrations (currently uses destructive fallback)
- [ ] Automated tests
- [ ] Matching local imports against Drive files to avoid duplicates

---

## Sample content

On first launch the app adds a sample EPUB, plus a public-domain
[LibriVox](https://librivox.org/) audiobook. The audiobook is added as a
placeholder and downloaded from archive.org on demand, so it isn't bundled in
the APK.
