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

The Android port is divided into five practical slices so the app stays usable
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

### Slice 2B: UX parity and interaction density

Deliverables:
- dashboard header that behaves like a compact travel instrument panel instead
  of a generic screen title plus route chips
- real quick actions in the top area for refresh and high-value flows instead
  of low-value navigation pills
- metric-led dashboard cards with stronger hierarchy, source badges, and
  compact secondary actions
- weather presentation that prioritizes current conditions, forecast drill-down,
  and surf context in the same section
- visited screen reworked around a map-first layout with compact stats and
  subdued capture guidance
- about/settings polish so those screens feel productized rather than scaffolded

Acceptance criteria:
- the compact phone dashboard is faster to scan and requires less vertical
  reading to answer the core questions: "am I connected?", "what is the
  weather?", and "what matters right now?"
- the visited screen exposes the world footprint before any explanatory copy
- Android screenshot reviews show closer parity with the macOS product's
  information hierarchy and action density while still respecting phone
  ergonomics

Status:
- Planned from the 2026-04-07 cross-platform screenshot review

### Slice 3: Local-first data parity

Deliverables:
- Visited places local persistence
- Visited country-day aggregation
- Time-tracking ledger and runtime state
- Foreground-service time tracking

Acceptance criteria:
- Data survives relaunch
- Corruption or missing storage is recoverable without app crash
- Time tracking can start, stop, resume, and persist state correctly

Status:
- Implemented for visited history and time-tracking runtime; metric history and
  export remain follow-up work

### Slice 4: Local-first data parity expansion

Deliverables:
- Metric history retention and history UI support
- time-tracking reporting/export
- JSON import/export where needed for inspection and recovery

Acceptance criteria:
- local history makes trend-oriented cards materially more useful
- time-tracking data is inspectable outside the app when needed
- corruption or missing storage is recoverable without app crash

Status:
- Partially scaffolded

### Slice 5: Provider-complete parity

Deliverables:
- Places-based emergency care integration
- travel-alert parity completion beyond the current aggregate/advisory/security
  baseline
- Travel weather alert decision and approved provider implementation
- surf/weather provider parity where the Android UX needs the same data density

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
- user-supplied keys stay local in encrypted device storage after install

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
- no secrets in source, resources, `BuildConfig`, or manifest placeholders
  committed to git or shipped in the app
- user-supplied provider credentials must be entered in-app and stored only in
  encrypted device-local storage

## Acceptance Criteria For The Next Milestone

The next substantive milestone after the current bootstrap should deliver:

- dashboard UX parity pass for the compact phone layout
- visited map-first UX pass
- enough screenshot coverage to review the new hierarchy against the macOS app

Definition of done:
- feature is documented in `features.md`
- status updated in `status.md`
- build/test/lint still pass
- user-visible behavior is testable on a real device
- `make screenshots` produces updated phone captures for the reworked screens

## Screenshot Gap Review (2026-04-07)

The Android app is currently behind the macOS app more in UX structure than in
raw feature count. The biggest differences are scan speed, action density, and
how quickly the UI surfaces the user's current travel situation.

### Highest-value work that is feasible now

- Replace the dashboard's route chips with true quick actions and make the top
  strip feel operational, not navigational.
- Recompose the dashboard so each card leads with one or two dominant metrics,
  then compact supporting detail, rather than several equally weighted text
  lines.
- Tighten weather into one coherent section with clear current conditions,
  forecast expansion affordances, and a compact surf/weather source badge.
- Reduce explanatory copy on the visited screen and promote the world map,
  travel counts, and source summary to the top of the screen.
- Add compact in-card actions where the Android data already exists, especially
  around fuel/map flows and other high-frequency review actions.
- Polish about/settings so they reinforce product identity instead of feeling
  like bootstrap scaffolding.

### Gaps that are real but better handled as separate follow-up work

- power and connectivity mini-chart parity requires retained metric history and
  history-driven UI, not just card redesign
- emergency care parity depends on Places integration and should remain its own
  provider slice
- time-tracking parity beyond the current local ledger needs reporting/export
  work and denser breakdown views
- full surf and travel-weather parity may require additional provider and data
  modeling decisions beyond the current dashboard scaffold
- macOS-specific affordances such as menu-bar workflows should be translated
  into Android-native quick actions rather than copied literally

## Risks

- Android background execution limits are stricter than macOS
- Maps/Places integration requires correct local config and restricted keys
- Travel weather alerts need a provider decision that fits client-side security
- it is easy to overfit the Android UI to the macOS layout; parity should target
  scan speed, action placement, and information density rather than desktop
  chrome
- wireless and metrics tooling can produce environment-specific warnings under
  sandboxed execution, but those are separate from app correctness

## Immediate Next Steps

1. Execute the dashboard UX parity pass first.
2. Follow immediately with the visited map-first UX pass.
3. Resolve the app-module KSP instability so screenshot review can stay in the
   iteration loop.
4. Re-run `make screenshots` after each parity slice and compare against the
   macOS reference set.
5. Queue emergency care, time-tracking reporting/export, and history-driven
   mini-chart work as separate follow-up tasks.
