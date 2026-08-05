# Mono

A personal fitness tracker for Android.  
Log activities, record live GPS sessions, plan routes, and review training stats — all on-device.

**Kotlin · Jetpack Compose · Room · Fused Location · Google Maps**

---

## What it does

- Activity feed with manual entry, edit, and delete
- Live GPS tracking with foreground service
- Post-activity map view and elevation profile
- GPX import and export
- Shareable PNG route cards
- Route planning with type and distance filters
- Weekly / monthly stats and personal records
- Optional BLE heart-rate strap support during recording

No account, no backend, no cloud sync. The app opens straight to the feed.

---

## Design

- Strict black and white
- Translucent, frosted-glass panels
- Rounded cards, pill buttons, soft shadows
- Bottom navigation: Home · Record · Routes · Stats

---

## Requirements

- Android Studio Koala+
- JDK 17
- Android SDK at the path in `local.properties`
- Google account for a **Maps SDK for Android** API key
- `minSdk 26`, `targetSdk 35`

---

## Get started

1. Open `C:\Users\MSI\OneDrive\Desktop\Interest\Mono` in Android Studio.
2. Copy `local.properties.example` to `local.properties` and fill in your paths.
3. Set your Google Maps API key in `local.properties`:

```properties
sdk.dir=C:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=AIza...your_real_key...
```

4. Run **app** on an emulator or device with API 26+.

---

## Google Maps setup

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project
3. Enable **Maps SDK for Android**
4. Create an API key and restrict it to Maps SDK for Android
5. Match your package name:
   - Debug: `com.mono.fitness.debug`
   - Release: `com.mono.fitness`

---

## Project layout

```
app/src/main/java/com/mono/fitness/
  data/           Room entities, DAOs, repository, seed data
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
| `ACCESS_BACKGROUND_LOCATION` | Background tracking continuation |
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

**No seed data**  
Uninstall the app or clear storage, then make sure `SEED_DATA=true` in `build.gradle.kts`.

---

## Seed data

On first launch with an empty database, Mono inserts sample activities and one planned route.

Toggle with:

```kotlin
buildConfigField("boolean", "SEED_DATA", "true")
```

Clear app data to re-seed, or use **Settings → Clear database** in the app.

---

## Roadmap

- Turn-by-turn navigation — Premium stub
- Training load score — Premium stub

---

## License

Personal and educational use.
