# Changelog

All notable changes to this project will be documented in this file.

## [0.7.4] - 2026-04-27

### Improved
- Refined dashboard header for long location names and prioritize location checking status.

## [0.7.3] - 2026-04-27

### Improved
- Enhanced dashboard header layout with inline chips and prioritize live signals in top bar.

### Fixed
- Fixed dashboard section cache serialization.

## [0.7.2] - 2026-04-24

### Fixed
- Harden location services and refine app settings defaults.

## [0.7.1] - 2026-04-23

### Added
- Implemented dashboard section caching and warm start.

### Build & Release
- Allow custom Google Play closed track names for publishing.
- Load local environment files for closed track publishing.
- Added regression checks for release helper closed track publishing.

## [0.7.0] - 2026-04-20

### Improved
- Enhanced dashboard header location comparison and advisory text processing.

### Build & Release
- Allow overriding Play Store release status during track publishing.

## [0.6.0] - 2026-04-09

### Added
- Replace Local Price Level with Local Info, adding public and school holidays.
- Added traveler warning chips for active public and school holidays.

### Fixed
- Ensure all default dashboard cards are present in user settings with correct default widths.
- Harden startup location bootstrap and single-flight dashboard refresh.

### Build & Release
- Added dedicated Play publishing targets for closed and production tracks.

## [0.5.1] - 2026-04-09

### Added
- Added first-run location onboarding to dashboard.
- Added Smartraveller detailed advice summary and source link.
- Replaced Local Price Level with Local Info, combining location context,
  public holidays, best-effort school holidays, and existing price signals.

## [0.5.0] - 2026-04-08

### Added
- Added time tracking interruption logging and focus reports.

### Fixed
- Enhanced time tracking UI, optimize data observation, and stabilize tests.

## [0.4.0] - 2026-04-08

### Added
- Added local price level indicators for Europe and the United States.

### Improved
- Enhanced Smartraveller advisory fetch with multiple fallbacks.
- Refined travel alert message for caution severity.
- Refined dashboard surf metric text sizes.

### Build & Release
- Enabled configurable Play Store release status and native crash symbolication.

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
