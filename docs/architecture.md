# Architecture

Last updated: 2026-04-30

## Overview

Nomad Dashboard Android is a single-activity, multi-module Kotlin app built
with Jetpack Compose. The architecture is intentionally split into:

- app shell and navigation
- feature UI modules
- shared model/design/data modules
- local-first storage
- provider-based external integrations

This mirrors the macOS separation between app shell, core logic, and reusable
UI, while following Android conventions.

## Module Map

### `app`

Responsibilities:
- application entrypoint
- `MainActivity`
- adaptive navigation
- manifest
- build variants and signing config

Key files:
- [MainActivity.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/main/java/com/iloapps/nomaddashboard/MainActivity.kt)
- [AndroidManifest.xml](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/main/AndroidManifest.xml)

### `core:model`

Responsibilities:
- app settings models
- dashboard snapshot models
- structured travel-alert domain vocabulary
- visited/time tracking domain vocabulary

Purpose:
- keep shared contracts stable across feature modules

### `core:designsystem`

Responsibilities:
- Nomad theme colors and shapes
- shared card and section components

Purpose:
- keep UI style consistent across screens

### `core:network`

Responsibilities:
- Retrofit services
- network response models

Purpose:
- isolate upstream API definitions and wire format concerns

### `core:datastore`

Responsibilities:
- Proto DataStore serializer and mapper
- persisted settings storage

Purpose:
- keep settings local and strongly typed

### `core:database`

Responsibilities:
- Room database
- DAOs and entities

Purpose:
- structured local persistence for historical and time-based data, including
  aggregate visited places, country-day rows, and chronological visited-place
  events

### `core:data`

Responsibilities:
- Hilt dependency graph
- repository implementation
- Android telemetry readers
- encrypted provider credential storage backed by Android Keystore
- Google Places-backed emergency-care lookup using the app-level Android
  Maps/Places key from manifest metadata
- Local Info orchestration with Room-backed holiday-response caching,
  Nager.Date public holidays, OpenHolidays subdivision and school-holiday
  matching, and reused Eurostat/HUD USER local price signals
- travel-alert provider orchestration and country-coverage resolution
- warm-start dashboard section cache hydration for location-dependent cards
- shared transient retry policy for startup-dependent remote fetches
- orchestration of local and remote data

Key files:
- [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt)
- [DefaultNomadDashboardRepository.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/repository/DefaultNomadDashboardRepository.kt)
- [SystemTelemetryReader.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/monitor/SystemTelemetryReader.kt)

### Feature modules

Responsibilities:
- one route/screen family per feature
- ViewModel + Compose screen

Current routes:
- dashboard
- settings
- visited
- time tracking
- about

## Data Flow

Current dashboard data flow:

1. `MainActivity` hosts navigation.
2. `DashboardViewModel` subscribes to repository state.
3. `DefaultNomadDashboardRepository` refreshes:
   - loads cached location-dependent dashboard sections first through
     `warmStart()` so cold launch can render the last-successful weather,
     travel alerts, local info, fuel, emergency care, and travel context data
     before fresh network work completes
   - Android connectivity and Wi-Fi state, including SSID, RSSI, link speed,
     and Wi-Fi band when the platform exposes them
   - retained connectivity metric history in Room for download, upload, and
     latency mini-charts
   - battery state, health/source/thermal diagnostics, and retained local
     battery percentage history for the power card chart
   - FreeIPAPI public IP and geolocation, with tolerant timezone parsing,
     retained last-known IP context when a later lookup fails, and an
     Android-only fallback path that resolves the raw IP first and then
     geolocates that address when the current-IP endpoint fails
   - single-flight refresh orchestration so overlapping startup/manual/
     permission-triggered refresh calls reuse the same in-flight bootstrap
     work instead of computing against different partial location state
   - staged location bootstrap that lets device and IP lookup race, prefers
     device context when it resolves first, falls back to IP context otherwise,
     and can promote location-dependent cards from IP fallback to device later
     in the same refresh cycle
   - current device coordinates whenever Android location permission is
     available, even when reverse geocoding has not resolved a place name yet
   - current device place whenever reverse geocoding succeeds so the dashboard
     can compare physical device location against public-IP location in the
     same Travel Context card
   - device-place-first, IP-fallback Local Info context with timezone-normalised
     public holidays, confident-only school-holiday subdivision matching, and
     embedded local price rows
   - device-coordinates-first fuel lookup context when fuel prices are enabled,
     with IP country fallback after the startup device-location phase settles
   - country-specific fuel provider selection for Spain, France, Italy, and Germany
   - device-place-first, IP-country-fallback travel-alert context
   - Smartraveller advisory lookup from the live destinations page for the
     resolved primary country plus bordering-country coverage, with Android
     fallbacks through the legacy `destinations-export` endpoint and a hidden
     WebView fetch when direct OkHttp transport stalls
   - ReliefWeb regional security lookup for the primary country plus bordering countries
   - Open-Meteo weather using the current device coordinate when the weather
     location toggle is enabled, otherwise the resolved IP-geolocation fallback
   - Open-Meteo hourly weather slots for `+3h`, `+6h`, and `+12h` dashboard
     checkpoints plus richer daily forecast metadata such as rain chance and wind
   - Open-Meteo marine forecast for the saved surf spot using the marine API
     plus the weather API’s wind series for near-term surf checkpoints
4. Repository emits a `DashboardSnapshot`.
5. Compose renders the snapshot through feature and design-system components.

Dashboard startup cache flow:

1. `DashboardViewModel` calls `repository.warmStart()` before the first live refresh.
2. `DefaultNomadDashboardRepository` reads `dashboard_section_cache` rows from Room.
3. The repository restores the last-successful location-dependent card snapshots
   into the shared `DashboardSnapshot`.
4. A live refresh then marks only those sections as refreshing, keeping their
   cached content visible until fresh data or a fallback state arrives.

Settings flow:

1. `SettingsViewModel` reads `AppSettings` from `NomadSettingsDataSource`.
2. `SettingsViewModel` reads provider credentials from the encrypted
   `ProviderCredentialStore`.
3. UI toggles emit update lambdas for general app settings.
4. Provider credential edits are saved explicitly from the Settings screen into
   Android Keystore-backed encrypted local storage.
5. Surf-spot edits are saved from the Settings screen as a name plus
   coordinates, and can be autofilled from the device’s current location on demand.
6. Repository persists general settings through Proto DataStore and refreshes
   dashboard state using the updated settings plus provider credentials.

Visited history flow:

1. `SettingsViewModel` persists `visitedPlacesEnabled` and
   `useCurrentLocationForVisitedPlaces`.
2. `DashboardViewModel` and `VisitedViewModel` trigger repository refreshes.
3. During refresh, the repository records one visited-history observation per
   capture. If device location resolves successfully, it records the device
   place and skips the public-IP observation for that capture. If device
   location is unavailable or unresolved, it falls back to public-IP
   geolocation when available.
4. `VisitedHistoryStore` writes the observation into a chronological
   `visited_place_events` table, merging same-place same-day observations into
   one event and keeping same-place different-day observations as distinct
   events.
5. `VisitedHistoryStore` also keeps the existing aggregate `visited_places`
   rows compatible and rebuilds observed plus inferred `visited_country_days`
   rows.
6. `VisitedViewModel` combines settings, aggregate places, country days, and
   event history into UI state for the visited screen.
7. `feature:visited` derives a feature-local map presentation from aggregate
   places and country days for World Footprint mode, and groups consecutive
   same-place events into selected-year travel stops for Travel Path mode.
   The visited screen can clear all three local history tables together.

Time tracking flow:

1. `TimeTrackingViewModel` and `DashboardViewModel` combine `AppSettings` with
   `TimeTrackingRepository` flows for projects, the active unallocated entry,
   pending buffered segments, recent allocated entries, and the interruption
   report snapshot.
2. `RoomTimeTrackingRepository` owns local project creation plus the
   continuous-capture lifecycle for entries stored in Room plus interruption
   events stored in a dedicated Room table.
3. The repository keeps one open unallocated entry at most. Closed unallocated
   entries act as the paused buffer waiting for allocation; allocated entries
   are normal completed ledger rows.
4. A built-in `Other` project is seeded locally and used both as the visible
   fallback allocation lane and as the internal placeholder project for
   unallocated capture rows.
5. The repository watches the configured same-day auto-tracking window and
   starts a new automatic unallocated row only when tracking is enabled, the
   current time is inside the window, and no paused buffer is waiting.
6. Reporting an interruption stores a timestamp plus the current entry id when
   one exists. Once that entry is allocated, the interruption is attributed to
   the chosen project in the local report model.
7. The repository derives day reports by combining allocated entry durations
   with interruption timestamps, then estimates focus loss as
   `interruptions x 23 minutes` and focused time as
   `allocated duration - estimated focus loss`.
8. `MainActivity`, the dashboard route, and the tracking route start or stop
   the app-owned foreground service from explicit user-visible capture actions.
9. `TimeTrackingForegroundService` reloads the active unallocated entry from
   persistence and rebuilds the persistent notification after app relaunch or
   service recreation, but not after device reboot.

Local Info flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves Local Info context in this order:
   - current device place plus reverse geocoding when Android permission is available
   - current public-IP geolocation fallback
3. The repository publishes a synchronized `checking` Local Info snapshot, then
   calls `LocalInfoProvider` with the resolved location, timezone, and HUD USER token.
4. `LocalInfoProvider` composes:
   - Nager.Date public holidays for the resolved country, plus the next year when needed near year end
   - OpenHolidays subdivision matching only when the region/locality match is unique and confident
   - OpenHolidays school holidays only for the confidently matched subdivision
   - the existing `LocalPriceLevelProvider` for compact price rows
   - Room-backed response caching for subdivision and holiday payloads with TTLs longer than the normal dashboard refresh cadence
5. Partial upstream failures stay local to their row/note and do not blank the full card.

Fuel prices flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves location context in this order:
   - current device coordinates when Android permission is already granted
   - current public-IP geolocation fallback
3. The repository creates a `FuelSearchRequest` with the resolved coordinate,
   ISO country code, and a fixed `50 km` radius.
4. `FuelPriceProvider` selects the matching source:
   - Spain ministry REST JSON
   - France government records API
   - Italy MIMIT daily CSV datasets
   - Germany Tankerkonig with encrypted in-app key storage
5. The provider returns the cheapest nearby diesel and gasoline stations, or an
   explicit unsupported/configuration/unavailable state.

Travel alerts flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves the travel-alert country context
   in this order:
   - current device place already available from the same startup-location cycle
   - current public-IP geolocation country fallback
3. The repository expands the resolved primary country into a coverage set of
   `primary + bordering countries` using a bundled border dataset.
4. The repository first publishes a synchronized `checking` travel-alert
   snapshot, then resolves each provider independently:
   - `SmartravellerAdvisoryProvider` for advisory severity on the primary country
   - `ReliefWebSecurityProvider` for regional security severity across the
     coverage set using the last `72 hours` of reports
5. Provider results are mapped into structured per-signal states:
   - `ready` when fresh data was resolved
   - `stale` when the latest refresh failed but a prior signal exists
   - `unavailable` when no prior signal exists or required context/config is missing

Emergency care flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves emergency-care search context in
   this order:
   - current device coordinates when Android location permission is already granted
   - current public-IP geolocation fallback
3. The repository publishes a synchronized `loading` emergency-care snapshot,
   then asks `GooglePlacesEmergencyCareProvider` for the nearest nearby
   hospital.
4. The provider uses Places Nearby Search (New) with a fixed `10 km` radius,
   filtered to the `hospital` primary type.
5. The dashboard maps provider output into `ready`,
   `configuration-required`, `permission-required`, `unavailable`, or `error`
   states and exposes an open-in-maps handoff only when a hospital is resolved.

Local price level flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves local-price context in this order:
   - current device place for country plus coordinate when Android permission is available
   - public-IP country fallback only for Europe when device location is unavailable
3. `DefaultLocalPriceLevelProvider` caches snapshots in Room for at least `6 hours`.
4. Europe requests use Eurostat `prc_ppp_ind_1` with `PLI_EU27_2020` and
   `ppp_cat18` values `A0111`, `A0101`, and `A01`, then map the latest
   available annual value into traveler-facing `Below Avg` / `Moderate` /
   `Above Avg` rows with `Country fallback` precision only.
5. US requests require the encrypted HUD USER token plus current device
   coordinates, resolve county GEOID through US Census Geocoder, then query HUD
   fair market rent data for the `One-Bedroom` benchmark and map the response
   into `metro` or `county` precision.
6. Repository output uses `ready`, `partial`, `location-required`,
   `configuration-required`, `unsupported`, or `unavailable` states and the
   dashboard always shows explicit source attribution.

## State Management Rules

- ViewModels own screen-level state exposure.
- Repositories own side effects and external IO.
- UI never talks directly to Retrofit, Room, or DataStore.
- Domain models live in `core:model`, not feature modules.
- Shared UI components stay in `core:designsystem`.

## Storage

### Implemented

- Proto DataStore for app settings
  Includes surf-spot name and coordinates used by the dashboard marine section
- Android Keystore-backed encrypted provider credential storage
- Room-backed metric points for retained connectivity throughput and latency
  history
- Room-backed metric points for retained battery percentage history
- Room-backed visited places with merged source provenance
- Room-backed visited country days with observed and inferred rows
- Room-backed time-tracking projects
- Room-backed time-tracking entries with project linkage and a single active
  entry invariant
- Room-backed local price level cache entries with a `6 hour` minimum TTL

## Current External Integrations

Implemented now:
- FreeIPAPI
- Open-Meteo
- Eurostat `prc_ppp_ind_1` for Europe local price levels
- HUD USER fair market rent plus US Census Geocoder for US local price levels
- Google Places Nearby Search (New) for emergency care
- Smartraveller travel advisories
- ReliefWeb regional security reports
- Spain fuel prices
- France fuel prices
- Italy fuel prices
- Germany Tankerkonig fuel prices with encrypted in-app key storage

Configured but not yet feature-complete:
- package/signature-restricted Google Maps key for the visited map
- future analytics ID

## Background and Runtime Strategy

Implemented now:
- foreground-only manual refresh model
- foreground service for active time tracking with a persistent notification and
  app-relaunch recovery

Planned:
- WorkManager for allowed periodic refresh work
- careful opt-in policy for any background location-history work beyond the
  current foreground refresh model

## Security and Configuration Rules

- No shared secrets in tracked config
- Release credentials only in gitignored local env files
- User-supplied provider credentials must never be compiled into `BuildConfig`,
  manifest placeholders, or resources
- user-supplied provider credentials remain local in Android Keystore-backed
  encrypted storage and are excluded from Android backup
- Android Maps SDK keys cannot use the in-app provider-credential flow because
  the SDK expects an app-level key before map init; this repo reads a local key
  from `local.properties`, Gradle properties, or environment variables and
  injects it only into the app manifest metadata for the current build
- the same app-level key also backs emergency-care Places lookups, so emergency
  care requires Places API (New) to be enabled on the local restricted key and
  does not use an in-app credential field

## Platform Adaptation Notes

Android differs from macOS in several areas:

- no WeatherKit
- no Apple Maps
- stricter background execution
- different Wi-Fi and battery telemetry APIs
- different release and signing pipeline

The architecture intentionally isolates those differences in `core:data` and
`core:network` rather than in UI modules.
