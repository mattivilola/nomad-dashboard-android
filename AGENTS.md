# AGENTS.md

Repository guidance for coding agents working on Nomad Dashboard Android.

## Non-Negotiables

- Never remove any files without the user's explicit consent.
- Do not revert user changes you did not make.
- Keep secrets out of git. Use local `Config/Signing.env` and `Config/AppConfig.env`.
- Never ship user-supplied API keys, app names, or similar provider credentials through `BuildConfig`, manifest placeholders, resources, or any other build-time injection path.
- User-managed provider credentials must be entered in-app after install and stored only in device-local encrypted storage backed by Android Keystore.
- Do not store secrets in Proto DataStore, Room, plain SharedPreferences, logs, screenshots, or Android backup payloads.
- Keep the project compatible with Android Studio on macOS.
- Update docs when behavior, scope, architecture, testing flow, or status changes.

## Start Here

Read these before making substantial changes:

1. `README.md`
2. `docs/status.md`
3. `docs/android-port-plan.md`
4. `docs/architecture.md`
5. `docs/features.md`
6. `docs/testing.md`
7. `docs/release.md` when touching signing or distribution

If the task is a parallel workstream, read the relevant handoff doc first.

## Project Shape

This is a Kotlin + Jetpack Compose, single-activity, multi-module Android app.

Main modules:
- `app`: entrypoint, navigation, manifest, app-level DI wiring
- `core:model`: shared domain models
- `core:designsystem`: theme and shared UI primitives
- `core:network`: Retrofit services and wire models
- `core:datastore`: Proto DataStore settings
- `core:database`: Room entities, DAOs, converters, database
- `core:data`: repositories, telemetry, provider orchestration, DI
- `feature:*`: screen-specific UI and ViewModel logic

Architecture rules:
- ViewModels expose immutable UI state.
- Repositories own IO and side effects.
- UI must not talk directly to Room, Retrofit, or DataStore.
- Shared domain types belong in `core:model`.
- Shared visual primitives belong in `core:designsystem`.

## Current Reality

Implemented and stable enough to build on:
- app shell and adaptive navigation
- dashboard/settings/about routes
- Proto DataStore settings
- Room scaffolding
- basic telemetry, IP geolocation, and Open-Meteo weather
- build/test/lint/run helper scripts
- wireless ADB helper
- Compose shell smoke tests

Active area:
- visited places persistence
- country-day aggregation logic

Before starting new work, check `docs/status.md` so you do not collide with the
current in-progress slice.

## Safe Parallelization

Before parallel work, assign clear ownership by module or file area.

Good parallel splits:
- `feature:*` UI work separated from provider integrations
- `core:network` parsing/provider work separated from `feature:*` presentation
- testing/docs tasks separated from active persistence/runtime tasks

Avoid parallel overlap in the same slice across:
- `core:model`
- `core:data`
- `core:database`

Those modules tend to be shared integration points and create merge friction.

## Build And Verification

Preferred commands:

```sh
make build
make test
make lint
make run
make connect-wireless
```

Key learnings so far:
- Use the repo helper scripts rather than raw Gradle where possible. They force
  Android Studio's bundled JDK instead of the shell's Java 11.
- `make test` runs unit tests first and only runs connected Android tests when
  `adb devices` shows an attached device in `device` state.
- Wireless ADB can drop unexpectedly. Reconnect before assuming an app/runtime
  regression.
- On Xiaomi/HyperOS devices, `INSTALL_FAILED_USER_RESTRICTED` is usually a
  phone-side security setting issue, not an app build issue.

If you change app behavior, rerun the relevant checks and update docs.

## Editing Rules

- Keep changes narrow and ownership-aware.
- Prefer additive edits over speculative refactors.
- Preserve module boundaries.
- Do not introduce hardcoded API keys or signing values.
- Do not reintroduce build-time env wiring for provider credentials that ship inside the app artifact.
- Use existing models/contracts before inventing new ones.
- Keep Android-specific adaptations in `core:data` and `core:network`, not in
  Compose UI code.

When touching storage:
- document schema/model changes
- add or update tests where practical
- keep migration implications visible in docs
- keep secret-bearing data in encrypted local storage and excluded from Android backup

When touching UI:
- preserve the existing Nomad visual identity
- keep phone and larger-screen behavior in mind
- add `testTag` hooks only when they materially help testing

## Docs Update Rules

Update these files when relevant:

- `docs/status.md`: current progress and verification state
- `docs/features.md`: user-visible feature parity changes
- `docs/architecture.md`: module/data-flow/storage changes
- `docs/testing.md`: workflow/test coverage changes
- `docs/release.md`: signing/publishing changes
- `README.md`, `AGENTS.md`, and `CLAUDE.md`: credential-handling policy changes

If you add a parallel-safe handoff task, document it explicitly.

## Handoff Standard

A good handoff includes:
- exact owned scope
- exact files/modules to avoid
- verification expectations
- doc updates required
- blockers encountered

Do not leave status ambiguous. If work is partially complete, mark it clearly in
`docs/status.md`.
