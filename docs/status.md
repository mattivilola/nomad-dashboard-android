# Status

Last updated: 2026-04-08

## Overall

Current phase: `Foundation + Core bootstrap complete`

Current repository state:
- initial Android port scaffold implemented
- initial Android scaffold committed
- documentation and wireless helper follow-up committed
- prior build/test/lint baseline verified locally
- real-device debug install verified on Android phone via wireless debugging
- physical-device-first `make run` targeting added for mixed phone + emulator setups
- fuel provider implementations and dashboard fuel card wiring implemented
- visited places persistence and country-day aggregation implemented
- visited screen history UI implemented
- visited device-location opt-in and permission CTA implemented
- visited map rendering implemented with Google Maps Compose, all-time place
  pins, and selected-year country shading
- time-tracking persistence, screen state, and foreground runtime implemented
- Compose UI smoke test suite added for the Android shell
- emulator-first connected-test workflow added
- encrypted device-local provider credential storage implemented for Germany
  Tankerkonig support
- travel-alert provider integrations implemented for Smartraveller advisory and
  ReliefWeb regional security
- ReliefWeb app name handling moved into encrypted device-local provider
  credentials and the Settings screen
- dashboard travel-alert card now renders aggregate, advisory, and regional
  security states from live provider-backed repository data
- emergency-care provider integration implemented with Google Places Nearby
  Search (New), device-location-first and public-IP fallback lookup, dashboard
  card state wiring, and open-in-maps handoff for resolved hospitals
- Android-versus-macOS screenshot review completed; the highest-value next
  parity work is dashboard and visited UX refinement rather than new visual
  theming alone
- dashboard UX parity pass started: compact quick actions, denser top-level
  summary strip, weather-first default ordering, and metric-led detail cards
  are now implemented in the dashboard UI layer
- dashboard header polish landed: duplicate top navigation and settings access
  are removed from the phone dashboard, the header now uses concrete location
  and freshness language, refresh shows visible progress, and the overview
  strip now leads with weather instead of a vague overall-status tile
- connectivity dashboard refinement landed: the card header now carries the
  live status chip, duplicate online copy is removed, empty throughput values
  render as `0 Mbps`, and retained Room-backed throughput/latency samples now
  drive real mini-charts
- whole-app UX parity pass expanded across settings, about, and time tracking;
  shared badges, metric blocks, chart shells, and stronger section headers now
  give the app a more coherent product language
- weather refresh now honors the existing device-location weather setting and
  falls back to IP geolocation when device coordinates are unavailable
- visited UX parity pass started: the screen now opens with an operational
  overview card and keeps capture guidance behind the map/history content
- screenshot review workflow now captures both light and dark theme exports for
  each review screen

## Completed

- Android Studio-compatible multi-module project created
- Gradle wrapper and version catalog created
- Makefile and helper scripts created
- Android Studio JDK auto-selection wired into scripts
- `make connect-wireless` helper added
- Compose app shell implemented
- adaptive navigation implemented
- Nomad theme and shared card components implemented
- Proto DataStore settings implemented
- Room database scaffold implemented
- repository and DI scaffold implemented
- live connectivity snapshot bootstrap implemented
- live power snapshot bootstrap implemented
- live public IP / IP geolocation bootstrap implemented
- live Open-Meteo weather bootstrap implemented
- dashboard route implemented
- weather location sourcing now respects the weather settings toggle and no
  longer depends only on IP geolocation
- settings route implemented
- about route implemented
- dashboard fuel card implemented with device-first / IP-fallback lookup
- dashboard emergency-care card implemented with loading, ready,
  permission-required, configuration-required, unavailable, and error states
- Spain ministry fuel provider implemented
- France government fuel provider implemented
- Italy MIMIT fuel provider implemented
- Germany Tankerkonig fuel provider implemented
- Germany Tankerkonig key moved to encrypted in-app settings storage
- Google Places Nearby Search (New) emergency-care provider implemented with
  local manifest-key configuration checks and hospital-only lookup
- visited route implemented
- visited places persistence implemented
- visited map rendering implemented
- country-day aggregation logic implemented
- time-tracking route implemented with local project creation, start/stop flow, and recent-session history
- time-tracking Room persistence implemented for projects and entries
- time-tracking foreground service and persistent notification implemented
- time-tracking repository/storage/runtime tests implemented
- Compose UI smoke tests added for app launch, top-level navigation, dashboard shell render, and one settings persistence flow
- emulator boot and emulator-targeted test helper scripts added
- deterministic emulator screenshot review workflow added with fixture-driven
  `make screenshots` exports to `output/screenshots/android/phone`
- physical-device-first `make run` device selection added with `ANDROID_SERIAL` override support
- release/signing/publish helper scripts scaffolded
- tracked changelog and Google Play release-notes generation added to the local release bump flow
- README and docs set added
- connectivity throughput/latency history retention and dashboard mini-charts
  implemented

## In Progress

- dashboard UX parity pass is underway; the first implementation landed for the
  compact phone dashboard, but screenshot verification is still pending
- visited UX parity pass is underway; the first implementation landed for a
  map-first screen structure, but screenshot verification and deeper polish are
  still pending
- settings/about/time-tracking UX parity pass is underway; the new productized
  layouts compile and test-compile, but still need screenshot review
- physical-device notification smoke verification for the new time-tracking runtime
- emulator-backed screenshot verification remains blocked intermittently by the
  local `Pixel_5_API_31` emulator exiting before it appears in `adb devices`

## Not Started

- power history visuals and retained battery charts
- time-tracking reporting/export parity
- analytics/privacy parity implementation

## Verification Status

Verified:
- `make build`
- `make lint`
- wireless ADB pairing and reconnect flow
- debug APK install on physical Android phone
- app launch on physical Android phone
- ADB target selection now prefers a connected physical device before any booted emulator
- visited model/store/repository unit coverage added and passing via `testDebugUnitTest`
- targeted compile verification for `:core:data` and `:feature:dashboard`
- direct `:app:assembleDebug` verification passed with
  `run_gradle -Pksp.incremental=false`
- travel-alert provider/resolver/repository unit coverage added and passing via
  `:core:data:testDebugUnitTest`
- dashboard travel-alert card Android test added and passing via
  `:feature:dashboard:connectedDebugAndroidTest`
- emergency-care provider/repository coverage now passes inside
  `:core:data:testDebugUnitTest`
- dashboard emergency-care card Android test added and passing via
  `:feature:dashboard:connectedDebugAndroidTest`
- time-tracking repository/storage/runtime coverage added and passing
- `make build` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `make lint` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `run_gradle -Pksp.incremental=false lintDebug` passed on 2026-04-07 after
  the travel-alert slice
- `run_gradle :core:model:compileDebugKotlin :feature:dashboard:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the first dashboard UX parity refactor
- `run_gradle :feature:visited:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the first visited UX parity refactor
- `run_gradle :core:designsystem:compileDebugKotlin :feature:dashboard:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:about:compileDebugKotlin :feature:timetracking:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the shared UX foundation and whole-app screen redesign pass
- `run_gradle :app:assembleDebug :app:compileDebugAndroidTestKotlin :feature:dashboard:compileDebugAndroidTestKotlin :feature:visited:compileDebugAndroidTestKotlin :feature:timetracking:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-07 after adding the new dashboard, visited, and time-tracking UI checks
- `run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the connectivity history and mini-chart slice
- `make build` passed on 2026-04-08 after the dashboard header and compact
  overview polish pass
- `make lint` passed on 2026-04-08 after the dashboard header and compact
  overview polish pass
- `:feature:dashboard:connectedDebugAndroidTest` passed on 2026-04-08 during
  the `make test` lane after the dashboard header and compact overview polish
- `make build` passed on 2026-04-07 after setting `ksp.incremental=false` in
  repo-level Gradle properties
- `make lint` passed on 2026-04-07 after setting `ksp.incremental=false` in
  repo-level Gradle properties
- release changelog/play-notes generator verified on 2026-04-07 in an isolated
  worktree for first-release, later-release, dirty-worktree, and tag-collision
  scenarios

Not yet fully re-verified after the whole-app UX parity slice in this session:
- `run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin :app:assembleDebug -Pksp.incremental=false`
  now gets through the updated repository test and dashboard compile path, but
  still fails later in existing app assembly work under `:app:compileDebugKotlin`
  with a missing `app/build/tmp/kotlin-classes/debug/com` output path
- `make screenshots` still cannot complete reliably when the local
  `Pixel_5_API_31` emulator exits before `adb` exposes a serial
- fresh light-and-dark screenshot exports for the redesigned dashboard,
  settings, visited, time-tracking, and about screens
- end-to-end physical-device smoke pass with the explicit `make test-device`
  path
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Resolve the current intermittent emulator launch crash, then rerun the full
   light-and-dark `make screenshots` parity pass.
2. Review the new dashboard and visited screenshots against the macOS
   reference set and tune spacing, card density, and vertical order from the
   rendered output.
3. Review the redesigned settings/about/time-tracking screens the same way and
   tighten any sections that still look form-heavy or generic.
4. Add retained-history-backed power visuals next so the remaining chart shell
   becomes a real telemetry module too.
5. Extend time tracking with reporting/export only after the current UX layer
   settles.

## Parallel-Safe Workstreams

Recommended secondary task while provider work is active:
- keep the emulator-first `make test` loop as default, then use `make test-device`
  only for explicit phone-side smoke checks

Handoff doc:
- [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)

## Update Instructions

When work lands:
- move finished items from `Not Started` to `Completed`
- add any active work to `In Progress`
- record new verification results
- keep this file chronological and factual rather than aspirational
