# Alex App Manager

An Android app manager for the apps published at
[github.com/shahzadp](https://github.com/shahzadp), plus the portfolio PWA.

It is deliberately **not** a browser wrapper: the catalogue is bundled, releases
are read from the GitHub API, and APKs are downloaded and installed inside the
app. There is no "open GitHub, hunt for the asset, open Files, tap the APK"
detour. The portfolio is the one exception — it opens in your browser so Android
can install it as a proper standalone PWA.

Built with Kotlin and Jetpack Compose using **Material 3 Expressive**
(`MaterialExpressiveTheme`, motion schemes, expressive shapes, flexible app bar,
loading indicators and pill navigation).

## Getting the app

Download the signed release APK from the
[Releases page](https://github.com/shahzadp/Alex-App-Manager/releases) and
sideload it (`Settings → install unknown apps` for your browser or file
manager). The app is the GitHub app manager, so once installed it handles APK
downloads and updates itself.

## What is in the catalogue

The list ships as `app/src/main/assets/catalog.json`, so a fresh install works
with no connection. Seven entries — six installable APKs and the portfolio PWA:

| App | Package | Section | Pinned release |
|---|---|---|---|
| Recorder by Alex | `com.alex.cam` | Apps | v1.0 |
| Photo Editor by Alex | `com.alex.photoeditor` | Apps | v1.0 |
| Alex Store | `io.github.shahzadp.alex_store` | Apps | v1.2.3 |
| 4 Games by Alex | `com.example.artgallery` | Games | v1.37 (`horizon` asset) |
| Project Alice | `org.projectalice.mobile` | Games | v0.6 |
| DoL by Alex | `com.vrelnir.dol` | Games | v0.5.13.6 |
| Portfolio | — (`https://shahzadp.github.io/portfolio/`) | Web | PWA |

Pinned versions, asset names and sizes are offline fallbacks. On launch the app
asks GitHub for the latest release of each repository, with ETag caching and a
30-minute TTL. If the network or the unauthenticated rate limit fails, the
bundled or cached values stay in place.

## Icons refresh themselves

Bundled icons are only the seed. On every refresh the app compares the current
release tag of each app against the tag stored beside its cached icon; if
GitHub has moved on, the release APK is downloaded once, its launcher icon is
extracted with `PackageManager.getPackageArchiveInfo`, and the library, detail
screen and settings tile switch to the new artwork automatically. Installing an
update through the manager caches the icon the same way. No manual icon edits
are needed for routine releases.

## How the install flow works

1. **Resolve** the latest release and pick the APK matching the catalogue's
   asset pattern (`assetPattern`) or the first `.apk` asset.
2. **Download** to app-private storage (`filesDir/apks`) with progress and
   speed updates. Cancelling deletes the partial file.
3. **Verify** before touching the installer:
   - the archive parses and its package name matches the catalogue;
   - the version code is newer than the installed copy;
   - for updates, the signing certificate matches the installed app.
4. **Install** through a `PackageInstaller` session. A mutable `PendingIntent`
   points at `InstallResultReceiver`, which launches the system confirmation
   screen and reports success or failure back into the UI.

If "install unknown apps" is not granted, the app explains why and deep-links to
the per-app setting (`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`).

No storage permission is needed: downloads live in internal storage and the
installer reads them through the session, not through the file system.

## The portfolio PWA

The portfolio is not faked as an APK and it is not cached inside the manager.
Tapping it opens `https://shahzadp.github.io/portfolio/` in your default
browser. The site is a real progressive web app, so the browser offers its own
"Add to Home screen" / install flow and keeps the offline cache itself. Alex App
Manager never stores a copy of the site.

## Project layout

```
app/src/main/
├── assets/catalog.json                 the fixed catalogue
├── java/io/github/shahzadp/appmanager/
│   ├── AlexApps.kt                     Application + container entry point
│   ├── AppContainer.kt                 small manual DI graph
│   ├── MainActivity.kt                 edge-to-edge Compose host
│   ├── data/                           catalogue, releases, installed apps, prefs
│   ├── install/                        download → verify → PackageInstaller
│   └── ui/                             root scaffold, library, detail, theme
└── res/                                icons, vector drawables, themes
```

Key files:

- `ui/AlexAppsApp.kt` — root scaffold, flexible top app bar, pill navigation,
  detail routing, settings sheet, snackbars, back handling.
- `ui/AppViewModel.kt` — combines releases, installed apps and install state
  into `LibraryState`.
- `install/InstallController.kt` — the download/verify/install pipeline.
- `ui/theme/Theme.kt` — Material 3 Expressive theme with dynamic colour on
  Android 12+ and hand-built fallback schemes.

## Building

Requirements:

- JDK 21
- Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`
- Gradle 9.3.1 (use the wrapper)
- AGP 9.1.0 with built-in Kotlin, KGP 2.3.20, Compose compiler 2.3.20

`compileSdk` is 37 because Compose 1.12 / Material3 1.5 require it. AGP 9.1
prints a "tested up to 36.1" warning; the build is verified and the warning is
suppressed in `gradle.properties`. AGP 9.2+ needs a newer Gradle than the one
pinned here.

On this machine the SDK lives at `D:\dev\SDKs\android-sdk` and Gradle's user
home at `D:\dev\BuildTools\gradle`:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat :app:assembleDebug --console=plain
```

The debug APK lands at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Tests and checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Covered: numeric version comparison / tag prefixes (`VersionsTest`) and
markdown cleanup plus byte formatting (`FormatTest`). Lint runs clean; the
remaining warnings are dependency-version suggestions and one Compose
deprecation.

## Release signing

The release build is signed only when the password is supplied; the keystore is
gitignored and never committed. It lives in the project root:

```
alex-app-manager.jks   (PKCS12, alias `alex-app-manager`, created 2026-11-08)
```

Its password was generated randomly and shown once at creation. If it is lost, the
keystore cannot be reused to update a published app — keep it somewhere safe.

Build a signed release APK by passing the password as a Gradle property (it is
read at configuration time and never committed):

```powershell
.\gradlew.bat :app:assembleRelease -PALEX_APPS_STORE_PASSWORD='<password>'
```

Optional overrides: `-PALEX_APPS_KEYSTORE=...`, `-PALEX_APPS_KEY_ALIAS=...`,
`-PALEX_APPS_KEY_PASSWORD=...`. Environment variables with the same names also
work. If no password is provided, `:app:assembleRelease` prints a notice and
produces an unsigned APK.

> Updates must be signed with the same key as the installed build. The manager
> refuses an update whose certificate differs and tells the user to uninstall
> first — that check is intentional, not a bug.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | GitHub release API, APK downloads, portfolio link |
| `ACCESS_NETWORK_STATE` | not essential today; kept for connectivity reporting |
| `REQUEST_INSTALL_PACKAGES` | hand downloaded APKs to the system installer |
| `REQUEST_DELETE_PACKAGES` | ask the system to uninstall a managed app (the system still asks the user to confirm) |

Package visibility is scoped with a `<queries>` list for the six known packages
plus launcher intents, so no broad `QUERY_ALL_PACKAGES` permission is needed.

## Notes and limits

- GitHub's unauthenticated API allows 60 requests an hour. Results are cached;
  the UI surfaces the limit instead of failing silently.
- The catalogue is static by design. Adding an app means editing
  `assets/catalog.json` (and adding its icon to `res/drawable-nodpi`).
- The portfolio opens in the external browser. Installing it, and caching it for
  offline use, is handled by the browser's PWA flow.
- DoL by Alex is an 18+ community fork; Project Alice needs the user's own copy
  of Victoria 2. Both caveats are shown on the detail screen.
