# Architecture

Last updated: 2026-04-07

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
- structured local persistence for historical and time-based data

### `core:data`

Responsibilities:
- Hilt dependency graph
- repository implementation
- Android telemetry readers
- encrypted provider credential storage backed by Android Keystore
- travel-alert provider orchestration and country-coverage resolution
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
   - Android connectivity and Wi-Fi state
   - battery and charging state
   - FreeIPAPI public IP and geolocation
   - device-first, IP-fallback fuel lookup context when fuel prices are enabled
   - country-specific fuel provider selection for Spain, France, Italy, and Germany
   - device-place-first, IP-country-fallback travel-alert context
   - Smartraveller advisory lookup for the resolved primary country
   - ReliefWeb regional security lookup for the primary country plus bordering countries
   - Open-Meteo weather using the resolved coordinate
4. Repository emits a `DashboardSnapshot`.
5. Compose renders the snapshot through feature and design-system components.

Settings flow:

1. `SettingsViewModel` reads `AppSettings` from `NomadSettingsDataSource`.
2. `SettingsViewModel` reads provider credentials from the encrypted
   `ProviderCredentialStore`.
3. UI toggles emit update lambdas for general app settings.
4. Provider credential edits are saved explicitly from the Settings screen into
   Android Keystore-backed encrypted local storage.
5. Repository persists general settings through Proto DataStore and refreshes
   dashboard state using the updated settings plus provider credentials.

Visited history flow:

1. `SettingsViewModel` persists `visitedPlacesEnabled` and
   `useCurrentLocationForVisitedPlaces`.
2. `DashboardViewModel` and `VisitedViewModel` trigger repository refreshes.
3. During refresh, the repository records:
   - public-IP geolocation observations when available
   - device-location observations when the visited-location opt-in is enabled
     and Android permission is granted
4. `VisitedHistoryStore` merges places in Room and rebuilds observed plus
   inferred country-day rows.
5. `VisitedViewModel` combines settings, visited places, and country days into
   UI state for the visited screen.
6. `feature:visited` derives a feature-local map presentation from the existing
   places and country days, then renders all-time place markers plus
   selected-year country shading from a bundled world-country GeoJSON asset.

Time tracking flow:

1. `TimeTrackingViewModel` combines `AppSettings` with `TimeTrackingRepository`
   flows for projects, the current active entry, and recent completed entries.
2. `RoomTimeTrackingRepository` owns local project creation plus the
   start/stop commands for entries stored in Room.
3. A single active session is represented only by an entry with `endAt == null`;
   repository transactions reject starting a second session while one is active.
4. `MainActivity` and the tracking route start or stop the app-owned foreground
   service from explicit user-visible tracking actions only.
5. `TimeTrackingForegroundService` reloads the active entry from persistence and
   rebuilds the persistent notification after app relaunch or service
   recreation, but not after device reboot.

Fuel prices flow:

1. `DashboardViewModel` triggers repository refresh.
2. `DefaultNomadDashboardRepository` resolves location context in this order:
   - current device location when Android permission is already granted
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
   - current device place already available from the same refresh cycle
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

## State Management Rules

- ViewModels own screen-level state exposure.
- Repositories own side effects and external IO.
- UI never talks directly to Retrofit, Room, or DataStore.
- Domain models live in `core:model`, not feature modules.
- Shared UI components stay in `core:designsystem`.

## Storage

### Implemented

- Proto DataStore for app settings
- Android Keystore-backed encrypted provider credential storage
- Room-backed visited places with merged source provenance
- Room-backed visited country days with observed and inferred rows
- Room-backed time-tracking projects
- Room-backed time-tracking entries with project linkage and a single active
  entry invariant

### Scaffolded

- Room database and DAOs for:
  - metric points

## Current External Integrations

Implemented now:
- FreeIPAPI
- Open-Meteo
- Smartraveller travel advisories
- ReliefWeb regional security reports
- Spain fuel prices
- France fuel prices
- Italy fuel prices
- Germany Tankerkonig fuel prices with encrypted in-app key storage

Configured but not yet feature-complete:
- package/signature-restricted Google Maps key for the visited map
- future Places user-provided credentials
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

## Platform Adaptation Notes

Android differs from macOS in several areas:

- no WeatherKit
- no Apple Maps
- stricter background execution
- different Wi-Fi and battery telemetry APIs
- different release and signing pipeline

The architecture intentionally isolates those differences in `core:data` and
`core:network` rather than in UI modules.
