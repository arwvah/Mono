# Mono

A personal fitness tracker for Android.
Log activities, record live GPS sessions, plan routes, and review training stats. Activities and routes are stored on-device.

**Kotlin · Jetpack Compose · Room · Fused Location · Google Maps**

---

## What it does

- Activity feed with manual entry, edit, and delete
- Live GPS tracking with foreground service and 1km milestone split haptics
- Kilometer splits breakdown with comparative relative pace bars and fastest split badges
- Estimated Best Efforts detection (fastest 400m, 1k, 1 mile, 5k, 10k segments)
- Estimated heart-rate zone distribution when average HR is available; clearly labeled as an estimate, not measured time in zones
- Interactive Workout Profile chart with instant toggle between Elevation and Pace / Speed curves
- Strava-style Weekly Goal progress tracker
- Post-activity map view and elevation profile
- GPX activity import and activity / planned-route export (tracks, route points, and waypoints supported on import)
- Shareable PNG route cards with true-aspect silhouette
- Route planning with tap-to-add waypoints, elevation lookup, and distance filters
- Weekly / monthly stats and auto-calculated personal records
- The recording service creates sessions on Start, commits GPS points incrementally, and completes Finish only after the final save succeeds
- Ordered, transactional recording saves with retry feedback; finishing also works when the Record screen is closed
- Clean local database with zero mock data (100% user-recorded sessions and GPX imports)
- Optional BLE heart-rate strap support during recording

No account, no app backend, no cloud sync. The app opens straight to the feed.

Google Maps loads map data over the network. Route planning sends waypoint coordinates to the Open-Meteo elevation API. Recording history is stored locally in Room; Android system backup may include app data because backup is enabled. Exported GPX files and PNG cards include route information.

---

## Design

- Strict black and white
- Translucent, frosted-glass panels
- Rounded cards, pill buttons, soft shadows
- Bottom navigation: Home · Record · Routes · Stats

---

## Requirements

- Android Studio with support for Android Gradle Plugin 8.7.2
- JDK 17
- Android SDK at the path in `local.properties`
- Google account for a **Maps SDK for Android** API key
- `minSdk 26`, `targetSdk 35`

---

## Get started

1. Clone this repository and open it in Android Studio.
2. Copy `local.properties.example` to `local.properties` and fill in your paths.
3. Set your Google Maps API key in `local.properties`:

```properties
sdk.dir=/path/to/your/android/sdk
MAPS_API_KEY=AIza...your_real_key...
```

4. Run **app** on an emulator with Google APIs or a physical device with Google Play services, API 26+.

Without a Maps key, builds and automated tests still work; map tiles require a valid key. Never commit `local.properties` or a signing keystore.

---

## Google Maps setup

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project
3. Enable **Maps SDK for Android**
4. Create an API key and restrict it to Maps SDK for Android
5. Add an Android application restriction using your signing certificate SHA-1 (`./gradlew signingReport`) and the package name:
   - Debug: `com.mono.fitness.debug`
   - Release: `com.mono.fitness`

---

## Project layout

```
app/src/main/java/com/mono/fitness/
  data/           Room entities, DAOs, repository
  tracking/       Foreground GPS recording service
  ui/
    theme/        Black/white theme, frosted panels
    components/   Cards, maps, charts
    screens/      Home, Record, Routes, Stats, CRUD
    navigation/   Bottom nav + navigation graph
  util/           Formatters, GPX parser, share PNG
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Live GPS tracking |
| `ACCESS_BACKGROUND_LOCATION` | Declared; the app does not request background-location access. Recording continues through its location foreground service |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Recording notification |
| `POST_NOTIFICATIONS` | Android 13+ notification permission |
| `BLUETOOTH*` | Optional heart-rate strap |
| `WRITE_EXTERNAL_STORAGE` | Save PNG cards on Android 28 and below |

---

## Troubleshooting

**Blank map**
Set a valid `MAPS_API_KEY`, enable Maps SDK in Google Cloud Console, and confirm the package name and SHA-1 match.

**Tracking stops when screen is off**
Disable battery optimization for Mono and use a physical device when possible.

**Gradle or Java errors**
Install JDK 17 and set it in Android Studio under **Settings → Build → Gradle → Gradle JDK**.

**An empty feed on first launch**
This is expected. Mono does not insert sample activities or routes. Record a session, add a manual activity, or import a GPX file.

**A save fails during recording**
Check available storage. Checkpoint failures retry on the next save; a failed Finish leaves the session paused with a Finish button to retry. Keep the app open until saving completes.

---

## Build and verify

Use the committed Gradle wrapper and JDK 17:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Unit tests cover formatting, distance math, GPX parsing and import limits, deterministic elevation batching, and recording save ordering / retry behavior.

With an API 26+ device or Google APIs emulator connected (CI uses API 35):

```sh
./gradlew connectedDebugAndroidTest
```

Instrumented tests cover Room persistence and rollback, personal-record recalculation, Android GPX parsing, duplicate recording Start / Finish commands, screen smoke tests, and PNG export. GitHub Actions runs both suites and lint, and uploads test reports and the debug APK.

## Data integrity and limits

- Activity and route updates commit metadata and GPS points in a single database transaction.
- Personal records are rebuilt for affected sports after activity edits and deletion, and after recording finishes.
- GPX imports reject DTDs and external entities, skip invalid coordinates, and accept up to **16 MiB / 100,000 points**. XML namespace prefixes and `<rtept>` routes are supported.
- Recording checkpoints preserve committed progress if the process is killed. Automatic resumption after process death is not implemented; any points not yet committed can be lost.
- Heart-rate zone percentages are estimates from summary HR values. Mono does not store a complete timestamped HR series, so measured time in zones is unavailable.
- Finish recording before editing its metrics or clearing the database in Settings. Clearing app storage or uninstalling deletes local history; export important activities first.

---

## Roadmap

- Turn-by-turn navigation — Premium stub
- Training load score — Premium stub

---

## License

Personal and educational use.
