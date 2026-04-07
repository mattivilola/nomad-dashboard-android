# Android Port Plan

Last updated: 2026-04-07

## Goal

Build a native Android app that preserves the macOS Nomad Dashboard product
shape, feature set, and visual identity while adapting implementation details to
Android-native APIs, Android lifecycle rules, and Google Play distribution.

## Product Decisions

- Product name: `Nomad Dashboard`
- Android application ID: `com.iloapps.nomaddashboard`
- Debug application ID suffix: `.dev`
- Minimum Android version: Android 11 / API 30
- Primary IDE: Android Studio
- Build JDK policy: prefer Android Studio bundled JDK 17+
- Delivery strategy: phased parity
- Device strategy: phone-first adaptive layouts, tablet/large-screen capable
- Platform parity rule: same user-facing behavior with Android-native internals
- Backend policy: no shared backend required for v1
- Release target: Google Play Internal testing first
- Analytics parity: later phase, not in the current bootstrap

## Scope Model

The Android port is divided into four practical slices so the app stays usable
early and parity can be closed incrementally without destabilizing the codebase.

### Slice 1: Foundation and runnable app shell

Deliverables:
- Android Studio-compatible multi-module project
- Compose navigation shell
- Hilt, Room, Proto DataStore, Retrofit/OkHttp setup
- Nomad-branded theme and dashboard card layout
- Build/test/lint/release helper scripts
- Runnable debug app on emulator or phone

Acceptance criteria:
- `make build` succeeds
- `make test` succeeds
- `make lint` succeeds
- Debug APK installs and launches

Status:
- Implemented

### Slice 2: Core parity

Deliverables:
- Connectivity snapshot
- Public IP and IP geolocation
- Power/battery snapshot
- Weather card via Open-Meteo
- Settings and About screens
- Dashboard refresh flow

Acceptance criteria:
- Main dashboard updates from live Android telemetry and network lookups
- Settings persist between launches
- Dashboard adapts for compact and larger screens

Status:
- Implemented as initial live bootstrap

### Slice 3: Local-first data parity

Deliverables:
- Metric history retention and history UI support
- Visited places local persistence
- Visited country-day aggregation
- Time-tracking ledger and runtime state
- Foreground-service time tracking
- JSON import/export where needed for inspection and recovery

Acceptance criteria:
- Data survives relaunch
- Corruption or missing storage is recoverable without app crash
- Time tracking can start, stop, resume, and persist state correctly

Status:
- Visited history slice implemented; metric history and time tracking remain scaffolded

### Slice 4: Provider-complete parity

Deliverables:
- Fuel provider integrations by country
- Places-based emergency care integration
- Maps-backed visited view
- Smartraveller and ReliefWeb parity completion
- Travel weather alert decision and approved provider implementation

Acceptance criteria:
- Regional providers behave as documented
- User-supplied secrets stay local
- No shared private secret is bundled into the app

Status:
- Scaffolded only

## Detailed Technical Plan

### 1. Repository and build system

Use a thin top-level `Makefile` and keep actual logic in `scripts/`.

Required tracked files:
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle.properties`
- `Config/version.properties`
- `Config/Signing.example.env`
- `Config/AppConfig.example.env`

Required helper scripts:
- `bootstrap.sh`
- `doctor.sh`
- `connect-wireless-device.sh`
- `build-dev.sh`
- `run-dev.sh`
- `rerun-dev.sh`
- `test.sh`
- `lint.sh`
- `probe-external-sources.sh`
- `bundle-release.sh`
- `apk-release.sh`
- `release-common.sh`
- `release-preflight.sh`
- `publish-internal.sh`
- `release.sh`
- `prepare-release.sh`

### 2. Module structure

Top-level app:
- `app`

Core shared modules:
- `core:common`
- `core:model`
- `core:designsystem`
- `core:network`
- `core:data`
- `core:database`
- `core:datastore`
- `core:testing`

Feature modules:
- `feature:dashboard`
- `feature:settings`
- `feature:visited`
- `feature:timetracking`
- `feature:about`

### 3. State and UI architecture

- Single-activity app with Navigation Compose
- One ViewModel per feature route
- Immutable UI state exposed via `StateFlow`
- Unidirectional data flow only
- Repository owns refresh orchestration and external lookup composition
- Compose screens render state and emit user actions upward

### 4. Data and storage strategy

Settings:
- Proto DataStore

Structured local records:
- Room database

Current planned storage groups:
- metric history
- visited places
- visited country days
- time-tracking entries

Constraints:
- local-first only
- no shared private credentials in tracked files or shipped bundle
- user-supplied keys stay local

### 5. Provider strategy

Keep identical or similar upstreams where safe and practical:

- Public IP / IP geolocation: FreeIPAPI
- Weather: Open-Meteo on Android
- Surf / marine: Open-Meteo
- Travel advisory: Smartraveller
- Regional security: ReliefWeb with approved app name from local config
- Germany fuel prices: user-supplied Tankerkonig key

Android-specific replacements:
- WeatherKit -> Open-Meteo
- Apple Maps emergency search -> Google Places / Maps
- Apple Maps map views -> Google Maps-backed Android UI

Deferred decision:
- Travel weather alerts

### 6. Release and distribution plan

Release artifact policy:
- canonical: signed `.aab`
- optional QA artifact: signed `.apk`

Distribution policy:
- Google Play Internal testing first
- promote later to closed/open/production after parity and policy review

Security policy:
- signing keys and Play service-account credentials are local only
- no secrets in source, resources, manifest placeholders committed to git

## Acceptance Criteria For The Next Milestone

The next substantive milestone after the current bootstrap should deliver:

- foreground-service time tracking skeleton
- dashboard history series persistence
- at least one live provider-complete card beyond weather and IP

Definition of done:
- feature is documented in `features.md`
- status updated in `status.md`
- build/test/lint still pass
- user-visible behavior is testable on a real device

## Risks

- Android background execution limits are stricter than macOS
- Maps/Places integration requires correct local config and restricted keys
- Travel weather alerts need a provider decision that fits client-side security
- wireless and metrics tooling can produce environment-specific warnings under
  sandboxed execution, but those are separate from app correctness

## Immediate Next Steps

1. Implement time-tracking persistence and foreground-service runtime.
2. Add release docs for signed internal AAB publishing with a real keystore.
3. Add manual screenshot capture process for Android parity reviews.
4. Add map rendering on top of the persisted visited-history slice.
