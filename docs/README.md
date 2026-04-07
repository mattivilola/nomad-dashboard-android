# Docs

This directory is the source of truth for the Android port’s product scope,
architecture, implementation plan, verification workflow, and current status.

## Documents

- [android-port-plan.md](./android-port-plan.md)
  Decision-complete Android implementation plan, including milestones,
  provider strategy, release workflow, and acceptance criteria.
- [architecture.md](./architecture.md)
  Current Android architecture, module structure, data flow, and technical
  conventions used in the codebase.
- [features.md](./features.md)
  Feature inventory, current parity status, and platform adaptation notes.
- [status.md](./status.md)
  Live execution tracker for the Android port. Update this as work lands.
- [testing.md](./testing.md)
  Local developer setup, phone/emulator testing, wireless debugging, scripted
  checks, and current manual verification checklist.
- [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)
  Compact parallel-safe handoff task for a second agent to add basic Compose
  UI smoke coverage without touching the visited-data implementation.
- [release.md](./release.md)
  Build, signing, and Google Play internal-track release workflow.

## Update Policy

- Update [status.md](./status.md) whenever implementation scope changes.
- Update [features.md](./features.md) when a user-visible feature changes.
- Update [architecture.md](./architecture.md) when modules, storage, DI, or
  data flow changes materially.
- Update [testing.md](./testing.md) when the supported local workflow changes.
- Update [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)
  when the recommended parallel handoff task changes materially.
- Update [release.md](./release.md) when signing or publishing behavior changes.
