# Parallel Task: UI Smoke Tests

Last updated: 2026-04-07

## Why This Is Safe In Parallel

Primary workstream now in progress:
- visited places persistence
- visited-country-day aggregation logic

That main work is expected to touch:
- `core:data`
- `core:database`
- `core:model`
- `feature:visited`

To avoid collisions, this second task should stay mostly inside:
- `app/src/androidTest`
- minimal production edits only for `Modifier.testTag(...)` support

Avoid editing the visited-data write path, Room visited entities/DAOs, and
visited aggregation logic in this parallel task.

## Goal

Add basic Compose UI smoke coverage for the current runnable Android shell so
we keep regression detection moving while the visited-data slice is under
active implementation.

## Scope

1. Add an app-launch smoke test that verifies the dashboard opens and the
   `Nomad Dashboard` title is visible.
2. Add a navigation smoke test that opens:
   - Dashboard
   - Settings
   - Visited
   - Tracking
   - About
3. Add a settings smoke test that toggles at least one persisted setting,
   such as `Expand weather forecast`.
4. Add a dashboard smoke assertion that verifies stable dashboard UI renders
   without depending on live network values.

## Constraints

- Prefer `app/src/androidTest`.
- Add only minimal `testTag` hooks where needed.
- Do not implement visited persistence in this task.
- Do not modify build, signing, or release scripts.
- Keep the task merge-safe against ongoing visited-data development.

## Suggested Files

Likely test files:
- `app/src/androidTest/java/com/iloapps/nomaddashboard/MainNavigationTest.kt`
- `app/src/androidTest/java/com/iloapps/nomaddashboard/SettingsSmokeTest.kt`

Likely minimal production touch points if selectors are needed:
- `app/src/main/java/com/iloapps/nomaddashboard/MainActivity.kt`
- `feature/dashboard/src/main/java/com/iloapps/nomaddashboard/feature/dashboard/DashboardScreen.kt`
- `feature/settings/src/main/java/com/iloapps/nomaddashboard/feature/settings/SettingsScreen.kt`

## Acceptance Criteria

- smoke tests pass locally
- `make test` still passes
- docs stay aligned with the new automated coverage

## Handoff Prompt

Read [status.md](./status.md), [testing.md](./testing.md), and
[architecture.md](./architecture.md). Add Compose UI smoke tests for the
current Android shell without touching the visited-place persistence
workstream. Keep edits mostly inside `app/src/androidTest` and only add
minimal `testTag` support where required. Verify with `make test`, then update
[status.md](./status.md) and [testing.md](./testing.md).
