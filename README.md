# Mono

Personal Strava-style fitness tracker for Android. **Kotlin · Jetpack Compose · Room · Fused Location · Google Maps**.

Everything is on-device. No backend, no login — the app opens on the activity feed.

## Design

- Strict black & white
- Frosted / bubble cards, pill buttons, soft shadows
- Bottom nav: **Home · Record · Routes · Stats**

## Features (v1)

| Area | Status |
|------|--------|
| Activity feed + manual CRUD | Done |
| Live GPS recording (foreground service) | Done |
| Post-activity map + elevation profile | Done |
| GPX import + export | Done |
| Shareable PNG route card | Done |
| Route planning (tap map) + elevation API + filters | Done |
| Home feed type filter + route min/max distance | Done |
| BLE heart-rate strap (scan + live HR HUD) | Done |
| Route + activity GPX export | Done |
| Settings → clear database | Done |
| Unit + Room + Compose UI tests | Done |
| Weekly/monthly stats + auto PRs | Done |
| Turn-by-turn nav | Stub — Premium |
| Training load score | Stub — Premium |
| Mock seed data | On by default (`BuildConfig.SEED_DATA`) |

---

## Requirements

- **Android Studio** Ladybug / Koala+ (AGP 8.7, JDK 17)
- **minSdk 26**, targetSdk 35
- Android SDK at the path in `local.properties`
- Google account for a **Maps SDK for Android** API key (maps won’t render without it; rest of the app works)

---

## Google Maps API key

1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Create/select a project → **APIs & Services → Library**
3. Enable **Maps SDK for Android**
4. **Credentials → Create credentials → API key**
5. Restrict the key to *Maps SDK for Android* and your package:
   - Debug: `com.mono.fitness.debug`
   - Release: `com.mono.fitness`
6. Copy `local.properties.example` → `local.properties` (Android Studio may already create `sdk.dir`)
7. Set:

```properties
sdk.dir=C\:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=AIza...your_real_key...
```

The key is injected into the manifest via `manifestPlaceholders`.

**Never commit a real key.** `local.properties` is gitignored.

---

## Open & run

1. **File → Open** → `C:\Users\MSI\OneDrive\Desktop\Interest\Mono`
2. Wait for Gradle sync (first run downloads the wrapper + deps)
3. If JDK is missing: **Settings → Build → Gradle → Gradle JDK → 17**
4. Pick an emulator (API 26+) or USB device with **Developer options**
5. Run **app**

### Phase test checklist

**Phase 1 — Scaffold**  
App launches, black/white theme, bottom nav switches Home / Record / Routes / Stats.

**Phase 2 — Manual entry**  
Home → **Add manual** → fill type/distance/duration → Save → appears in feed → open detail → Edit / Delete.  
Home → **Import GPX** → pick a `.gpx` file → activity opens with map + stats.

**Phase 3 — GPS**  
Record tab → grant location (+ notifications on Android 13+) → allow battery unrestricted when prompted → Start → notification “Recording” → lock screen → unlock → track still growing → Finish → detail with map.

**Phase 4 — Route / elevation / GPX**  
Open a seeded activity → map + elevation chart → toolbar export GPX → share sheet.

**Phase 5 — Share card**  
Detail → share icon or **Share PNG card** → gallery `Pictures/Mono` + share sheet (silhouette path, no tiles).

**Phase 6 — Routes**  
Routes → **Plan** → tap map ≥2 points → name → Save → filter by type/distance. Turn-by-turn shows Premium stub.

**Phase 7 — Stats**  
Stats → week/month totals, 7-day bar chart, personal records from seed data. Training load = Premium stub.

---

## Seed data

On first launch with empty DB, Mono inserts:

- GPS run, ride, hike (with track points)
- Manual gym session
- One planned route

Toggle in `app/build.gradle.kts`:

```kotlin
buildConfigField("boolean", "SEED_DATA", "true") // or "false"
```

Clear app data to re-seed, or use **Home → Settings (gear) → Clear database**.

---

## Project layout

```
app/src/main/java/com/mono/fitness/
  data/          Room entities, DAOs, repository, seed
  tracking/      Foreground GPS service
  ui/
    theme/       B/W + frosted panels
    components/  Cards, map, charts
    screens/     Home, Record, Routes, Stats, CRUD
    navigation/  Bottom nav + graph
  util/          Formatters, GPX, share PNG
```

---

## Permissions

| Permission | Why |
|------------|-----|
| `ACCESS_FINE_LOCATION` | Live track |
| `ACCESS_BACKGROUND_LOCATION` | Continue when app backgrounded (user grants in settings) |
| `FOREGROUND_SERVICE` / `_LOCATION` | Recording notification service |
| `POST_NOTIFICATIONS` | Android 13+ recording notification |
| `BLUETOOTH*` | Optional HR strap during live recording |
| `WRITE_EXTERNAL_STORAGE` (≤28) | Legacy PNG save; 10+ uses MediaStore |

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Blank map | Set valid `MAPS_API_KEY`; enable Maps SDK; match package name + SHA-1 |
| Tracking dies screen-off | Disable battery optimization for Mono; use real device |
| Gradle / Java errors | Install JDK 17; set Gradle JDK in Android Studio |
| No seed data | Uninstall app or clear storage; ensure `SEED_DATA=true` |

---

## License

Personal / educational use.
