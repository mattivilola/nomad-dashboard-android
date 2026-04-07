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

### Scaffolded

- Room database and DAOs for:
  - metric points
  - time-tracking entries

## Current External Integrations

Implemented now:
- FreeIPAPI
- Open-Meteo

Configured but not yet feature-complete:
- ReliefWeb app name local config
- future Maps/Places keys
- future analytics ID

## Background and Runtime Strategy

Implemented now:
- foreground-only manual refresh model

Planned:
- WorkManager for allowed periodic refresh work
- foreground service for time tracking
- careful opt-in policy for any background location-history work beyond the
  current foreground refresh model

## Security and Configuration Rules

- No shared secrets in tracked config
- Release credentials only in gitignored local env files
- Android Maps/Places keys must be restricted to package and signing cert
- user-supplied provider credentials remain local

## Platform Adaptation Notes

Android differs from macOS in several areas:

- no WeatherKit
- no Apple Maps
- stricter background execution
- different Wi-Fi and battery telemetry APIs
- different release and signing pipeline

The architecture intentionally isolates those differences in `core:data` and
`core:network` rather than in UI modules.
