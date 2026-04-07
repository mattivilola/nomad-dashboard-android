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
   - Open-Meteo weather using the resolved coordinate
4. Repository emits a `DashboardSnapshot`.
5. Compose renders the snapshot through feature and design-system components.

Settings flow:

1. `SettingsViewModel` reads `AppSettings` from `NomadSettingsDataSource`.
2. UI toggles emit update lambdas.
3. Repository persists settings through Proto DataStore.
4. Repository refreshes dashboard state using the updated settings.

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
   - Germany Tankerkonig with local-only key config
5. The provider returns the cheapest nearby diesel and gasoline stations, or an
   explicit unsupported/configuration/unavailable state.

## State Management Rules

- ViewModels own screen-level state exposure.
- Repositories own side effects and external IO.
- UI never talks directly to Retrofit, Room, or DataStore.
- Domain models live in `core:model`, not feature modules.
- Shared UI components stay in `core:designsystem`.

## Storage

### Implemented

- Proto DataStore for app settings
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
- Spain fuel prices
- France fuel prices
- Italy fuel prices
- Germany Tankerkonig fuel prices with local-only key config

Configured but not yet feature-complete:
- ReliefWeb app name local config
- future Maps/Places keys
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
- Android Maps/Places keys must be restricted to package and signing cert
- user-supplied provider credentials remain local
- `NOMAD_TANKERKOENIG_API_KEY` is read from local `Config/AppConfig.env` only;
  the Android app does not ship a shared Germany fuel key

## Platform Adaptation Notes

Android differs from macOS in several areas:

- no WeatherKit
- no Apple Maps
- stricter background execution
- different Wi-Fi and battery telemetry APIs
- different release and signing pipeline

The architecture intentionally isolates those differences in `core:data` and
`core:network` rather than in UI modules.
