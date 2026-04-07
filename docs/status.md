# Status

Last updated: 2026-04-07

## Overall

Current phase: `Foundation + Core bootstrap complete`

Current repository state:
- initial Android port scaffold implemented
- initial Android scaffold committed
- documentation and wireless helper follow-up committed
- build/test/lint verified locally
- real-device debug install verified on Android phone via wireless debugging
- visited places persistence and country-day aggregation work has started
- Compose UI smoke test suite added for the Android shell

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
- settings route implemented
- about route implemented
- visited route placeholder implemented
- time-tracking route placeholder implemented
- Compose UI smoke tests added for app launch, top-level navigation, dashboard shell render, and one settings persistence flow
- release/signing/publish helper scripts scaffolded
- README and docs set added

## In Progress

- visited places persistence
- country-day aggregation logic

## Not Started

- visited map rendering
- time-tracking persistence and foreground-service runtime
- fuel provider implementations
- emergency care / Places integration
- Smartraveller and ReliefWeb Android provider implementation
- analytics/privacy parity implementation
- screenshot capture workflow for Android parity reviews

## Verification Status

Verified:
- `make build`
- `make test`
- `make lint`
- wireless ADB pairing and reconnect flow
- debug APK install on physical Android phone
- app launch on physical Android phone

Not yet verified in this session:
- `make test` after smoke-test addition is currently blocked by compile errors in [VisitedModels.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/model/src/main/java/com/iloapps/nomaddashboard/core/model/VisitedModels.kt) from the active visited-data workstream
- connected Compose smoke test execution after the current visited-data compile blocker is resolved
- run on emulator
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Finish visited place persistence and visited-country-day logic.
2. Capture first Android screenshots from the running app for parity review.
3. Implement local time-tracking runtime and storage.
4. Add provider-complete fuel and emergency-care slices.

## Parallel-Safe Workstreams

Recommended secondary task while visited-data work is active:
- rerun `make test` once the visited-data compile blocker is resolved so the new
  Compose smoke suite can execute on a connected target

Handoff doc:
- [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)

## Update Instructions

When work lands:
- move finished items from `Not Started` to `Completed`
- add any active work to `In Progress`
- record new verification results
- keep this file chronological and factual rather than aspirational
