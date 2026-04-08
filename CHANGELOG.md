# Changelog

All notable changes to this project will be documented in this file.

## [0.3.0] - 2026-04-08

### Added
- Implemented emergency care dashboard with Google Places.
- Implemented major design system overhaul and UI refresh.
- Implemented retained connectivity history and dashboard mini-charts.
- Implemented detailed power telemetry and battery history tracking.

### Improved
- Respect settings for weather location sourcing.
- Improved Google Places API key retrieval and clarify debug key configuration.
- Enhanced dashboard with richer weather, marine, power, and travel context.
- Enhanced dashboard with device location context and detailed WiFi information.
- Redesign time tracking with unallocated capture and auto-window.
- Refined dashboard wind summary and marine metrics presentation.
- Enhanced Smartraveller API robustness and refine time tracking UI and state.

### Build & Release
- Enhanced Emergency Care card UI and testability.
- Added test tag to Visited Overview Card for UI testing.
- Added UI test for Dashboard screen rendering.
- Added UI test for Time Tracking screen active state.

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
