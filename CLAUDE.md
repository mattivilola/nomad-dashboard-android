# CLAUDE.md

Claude-specific working notes for this repository. These are aligned with
`AGENTS.md` and should stay consistent with it.

## Primary Objective

Continue the Android port in a way that is:
- native to Android
- compatible with Android Studio on macOS
- local-first for storage and configuration
- safe for staged Google Play distribution later

Preserve feature parity with the macOS app where planned, but adapt internals
to Android-native APIs and constraints.

## First Read

Before coding, read:

1. `AGENTS.md`
2. `README.md`
3. `docs/status.md`
4. `docs/android-port-plan.md`
5. `docs/architecture.md`
6. `docs/features.md`
7. `docs/testing.md`

If the task is release-related, also read `docs/release.md`.

## Implementation Defaults

Prefer:
- Kotlin + Compose patterns already present in the repo
- ViewModel + repository + immutable state flow
- Room for structured local persistence
- Proto DataStore for app settings
- Android-specific integration logic inside `core:data` or `core:network`

Avoid:
- pushing storage or network logic into UI modules
- broad refactors during feature delivery
- changing unrelated modules while another workstream is active
- introducing secrets into tracked files
- compiling user-supplied provider credentials into the app via `BuildConfig`, manifest placeholders, resources, or any other build-time path

## Best Practices For This Repo

- Work from the current docs, not assumptions.
- Check `docs/status.md` before choosing a task.
- Keep ownership boundaries explicit when running parallel work.
- Use the Makefile/script workflow for verification.
- Update docs in the same change when implementation scope changes.
- Keep the app runnable in Android Studio after your change.

## Learnings So Far

- The shell's default Java may be wrong for Android builds. Use the repo
  helpers so the Android Studio JDK is selected automatically.
- Wireless debugging is useful for real-device testing, but disconnects are
  common and should not be mistaken for app failures.
- Physical install failures on Xiaomi/HyperOS can come from device-side
  restrictions like `Install via USB` and security settings.
- Parallel work is safest when split by module ownership rather than by vague
  feature labels.
- `core:model`, `core:data`, and `core:database` are the highest-conflict
  areas; avoid overlapping edits there across simultaneous tasks.
- Smoke tests are valuable, but connected-test execution depends on both an
  attached device and a compiling app graph.

## Documentation Discipline

When your work changes the repo materially, update the relevant docs:

- `docs/status.md` for progress and verification
- `docs/features.md` for feature parity
- `docs/architecture.md` for module/storage/data-flow changes
- `docs/testing.md` for new test coverage or workflow changes
- `docs/release.md` for build/signing/publish changes

If your task was created as a handoff, keep the handoff doc accurate too.

## Safety Rules

- Never remove files without explicit user approval.
- Never overwrite or revert user work unless explicitly asked.
- Do not commit secrets, service account files, or keystore materials.
- Do not store provider credentials in Proto DataStore, Room, plain SharedPreferences, or logs.
- User-supplied provider credentials must be entered after install and stored only in Android Keystore-backed encrypted local storage.
- Do not enable Android backup for secret-bearing app data.
- Do not claim verification you did not run.
- If blocked by compile failures from another active workstream, document the
  blocker clearly instead of masking it.

## Good Final Output

A good completion note for this repo should include:
- what changed
- what was verified
- what remains blocked or deferred
- which docs were updated

Keep it factual and implementation-oriented.
