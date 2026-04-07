# Status

Last updated: 2026-04-07

## Overall

Current phase: `Foundation + Core bootstrap complete`

Current repository state:
- initial Android port scaffold implemented
- initial Android scaffold committed
- documentation and wireless helper follow-up committed
- prior build/test/lint baseline verified locally
- real-device debug install verified on Android phone via wireless debugging
- fuel provider implementations and dashboard fuel card wiring implemented
- visited places persistence and country-day aggregation implemented
- visited screen history UI implemented
- visited device-location opt-in and permission CTA implemented
- Compose UI smoke test suite added for the Android shell
- emulator-first connected-test workflow added

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
- dashboard fuel card implemented with device-first / IP-fallback lookup
- Spain ministry fuel provider implemented
- France government fuel provider implemented
- Italy MIMIT fuel provider implemented
- Germany Tankerkonig fuel provider implemented with local-only config
- visited route implemented
- visited places persistence implemented
- country-day aggregation logic implemented
- time-tracking route placeholder implemented
- Compose UI smoke tests added for app launch, top-level navigation, dashboard shell render, and one settings persistence flow
- emulator boot and emulator-targeted test helper scripts added
- release/signing/publish helper scripts scaffolded
- README and docs set added

## In Progress

- verification rerun for build/lint/test after the fuel slice

## Not Started

- visited map rendering
- time-tracking persistence and foreground-service runtime
- emergency care / Places integration
- Smartraveller and ReliefWeb Android provider implementation
- analytics/privacy parity implementation
- screenshot capture workflow for Android parity reviews

## Verification Status

Verified:
- `make build`
- wireless ADB pairing and reconnect flow
- debug APK install on physical Android phone
- app launch on physical Android phone
- visited model/store/repository unit coverage added and passing via `testDebugUnitTest`
- targeted compile verification for `:core:data` and `:feature:dashboard`

Blocked in this session:
- `make lint` on 2026-04-07 still fails outside the fuel slice
  - `:app:lintDebug` reports existing time-tracking service permission errors in
    `TimeTrackingForegroundService.kt`
- `make test` on 2026-04-07 still fails outside the fuel slice
  - `:core:data:compileDebugUnitTestKotlin` fails on unrelated
    `RoomTimeTrackingRepositoryTest` errors already present in the worktree
  - connected tests still hit the earlier
    `:feature:dashboard:connectedDebugAndroidTest` and
    `:app:connectedDebugAndroidTest` failures

Not yet fully re-verified after the fuel slice in this session:
- full `make lint`
- full `make test` completion with the new default emulator path
- end-to-end physical-device smoke pass with the explicit `make test-device` path
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Implement local time-tracking runtime and storage.
2. Add emergency care / Places provider completion.
3. Capture first Android screenshots from the running app for parity review.
4. Add visited map rendering on top of the persisted history slice.

## Parallel-Safe Workstreams

Recommended secondary task while visited-data verification is active:
- rerun the emulator-first test flow end to end, then keep the phone for
  explicit smoke checks only

Handoff doc:
- [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)

## Update Instructions

When work lands:
- move finished items from `Not Started` to `Completed`
- add any active work to `In Progress`
- record new verification results
- keep this file chronological and factual rather than aspirational
