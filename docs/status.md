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
- visited route implemented
- visited places persistence implemented
- country-day aggregation logic implemented
- time-tracking route placeholder implemented
- Compose UI smoke tests added for app launch, top-level navigation, dashboard shell render, and one settings persistence flow
- emulator boot and emulator-targeted test helper scripts added
- release/signing/publish helper scripts scaffolded
- README and docs set added

## In Progress

- verification rerun for build/lint/full emulator-first test flow after the visited slice

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
- `make lint`
- wireless ADB pairing and reconnect flow
- debug APK install on physical Android phone
- app launch on physical Android phone
- visited model/store/repository unit coverage added and passing via `testDebugUnitTest`

Not yet fully re-verified after the visited slice in this session:
- full `make build`
- full `make lint`
- full `make test` completion with the new default emulator path
- end-to-end physical-device smoke pass with the explicit `make test-device` path
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Implement local time-tracking runtime and storage.
2. Capture first Android screenshots from the running app for parity review.
3. Add provider-complete fuel and emergency-care slices.
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
