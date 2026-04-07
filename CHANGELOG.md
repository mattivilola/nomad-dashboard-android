# Changelog

All notable changes to this project will be documented in this file.

## [0.2.0] - 2026-04-07

### Added
- Added custom application launcher icons.
- Implemented detailed Emergency Care dashboard with Places integration.

### Improved
- Move ReliefWeb app name to encrypted settings; refine Maps API key config.
- Refined compact dashboard and visited screen UX.
- Removed security best practices report.

## [0.1.0] - 2026-04-07

### Added
- Added the initial Android app shell with the local-first Nomad Dashboard foundation.
- Added visited places history with country-day aggregation.
- Added dashboard fuel prices across Spain, France, Italy, and Germany.
- Added local time tracking with projects, session history, and a persistent foreground timer.
- Added encrypted on-device storage for provider credentials entered in Settings.
- Added travel alerts plus a visited world map with saved-place pins and yearly country shading.

### Improved
- Improved the time-tracking flow and polished the local Android run workflow.

### Build & Release
- Added Android release and install helper tooling for local development.
- Improved the wireless ADB helper flow with explicit pairing-code prompts.
- Added a Compose UI smoke-test suite and tightened the repo test workflow.
- Improved Android instrumentation-test coverage and smoke-test reliability.
- Configured the default connected-test workflow to target the emulator first.
- Added deterministic emulator screenshot capture for UI review.
