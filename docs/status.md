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
- dashboard travel-alert card now renders aggregate, advisory, and regional
  security states from live provider-backed repository data

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
- Germany Tankerkonig fuel provider implemented
- Germany Tankerkonig key moved to encrypted in-app settings storage
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
- README and docs set added

## In Progress

- physical-device notification smoke verification for the new time-tracking runtime
- repo-level `make build` and `make lint` verification are currently blocked by
  unstable generated KSP app outputs under
  `app/build/generated/ksp/debug/java`

## Not Started

- emergency care / Places integration
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
- time-tracking repository/storage/runtime coverage added and passing
- `make build` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `make lint` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `run_gradle -Pksp.incremental=false lintDebug` passed on 2026-04-07 after
  the travel-alert slice

Not yet fully re-verified after the visited map slice in this session:
- `make build` currently fails in `:app:kspDebugKotlin` because generated app
  KSP output under `app/build/generated/ksp/debug/java` becomes unreadable
- `make lint` currently fails for the same `:app:kspDebugKotlin`
  generated-source issue even though direct `lintDebug` with
  `-Pksp.incremental=false` passed
- `make test` currently still fails in unrelated emulator connected-test suites
  under `feature:visited` and `app`; the new dashboard travel-alert test was
  fixed and passed when rerun directly
- `make screenshots` could not be run end-to-end for the same reason: the app
  module's generated KSP state makes full app verification unreliable
- end-to-end physical-device smoke pass with the explicit `make test-device`
  path
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Add emergency care / Places provider completion.
2. Resolve the current app-module KSP generated-source instability, then rerun
   `make build`, `make lint`, and `make screenshots`.
3. Fix or isolate the unrelated `feature:visited` connected-test failure, then
   rerun `make test`.
4. Extend time tracking beyond the local ledger with reporting/export only after the base slice settles.

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
