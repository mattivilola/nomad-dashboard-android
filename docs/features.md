# Feature Inventory

Last updated: 2026-04-08

This document tracks Android parity against the macOS app at the feature level.

## Status Legend

- `Implemented`: feature works in the Android app now
- `Bootstrap`: feature exists in UI/API shape but is not complete
- `Planned`: feature is documented but not implemented yet
- `Deferred`: feature intentionally not implemented until a provider or policy
  decision is approved

## Dashboard And Core Shell

| Feature | Android status | Notes |
| --- | --- | --- |
| Single main dashboard screen | Implemented | Adaptive Compose route is live and the compact dashboard now uses a location-led header, visible refresh progress, a weather-first three-up overview strip, and metric-led detail cards without duplicate top navigation |
| Settings screen | Implemented | Settings now use grouped control-room sections, clearer toggle descriptions, and stronger local-first credential messaging |
| About screen | Implemented | About now explains product purpose, local-first data handling, and current parity direction instead of bootstrap filler |
| Visited screen route | Implemented | Route now opens with a capture/control overview card, then the world map, local history summaries, saved places, and country-day breakdowns |
| Time tracking route | Implemented | Route now shares the same product language as the rest of the app, with clearer overview, active-state, and recent-session sections |
| Adaptive phone/tablet navigation | Implemented | Bottom bar on compact, rail on wider layouts |
| Nomad visual identity | Implemented | Warm sand / teal / coral card-based UI |
| Compact dashboard UX parity pass | Implemented | Default dashboard order now starts with weather and travel alerts; the phone dashboard removes duplicate top navigation, replaces the vague overall tile with weather-led overview tiles, and shows active refresh feedback |
| Shared product UI foundation | Implemented | Design system now includes stronger status badges, metric blocks, chart shells, and product top-bar/header patterns reused across screens |

## Connectivity And Travel Context

| Feature | Android status | Notes |
| --- | --- | --- |
| Internet reachability summary | Implemented | Uses `ConnectivityManager` |
| Latency probe | Implemented | Socket connect probe with retained local history now shown on the dashboard mini-chart |
| Passive throughput estimate | Implemented | Based on `TrafficStats` delta with retained local download/upload history for the dashboard chart |
| Wi-Fi SSID / RSSI | Implemented | Uses `WifiManager`; current API is basic |
| VPN detection | Implemented | Uses network transport check |
| Public IP lookup | Implemented | FreeIPAPI |
| IP geolocation | Implemented | FreeIPAPI |
| Travel context card | Implemented | Uses current bootstrap geolocation data |

## Dashboard UX polish

| Feature | Android status | Notes |
| --- | --- | --- |
| Connectivity card hierarchy | Implemented | Status chip now lives in the card header, duplicate online copy is removed, and empty throughput values default to `0 Mbps` instead of `n/a` |
| Connectivity mini-charts | Implemented | Throughput and latency panels now render simple retained-history charts from local Room-backed metric samples |

## Power

| Feature | Android status | Notes |
| --- | --- | --- |
| Battery percentage | Implemented | From battery intent |
| Charging state | Implemented | From battery intent |
| Battery health summary | Implemented | Heuristic bootstrap summary |
| Discharge watts estimate | Implemented | Uses current-now and voltage where available |
| Detailed power diagnostics | Bootstrap | Stronger power card layout and chart shells are in place; retained-history telemetry is still missing |

## Weather And Surf

| Feature | Android status | Notes |
| --- | --- | --- |
| Current weather | Implemented | Open-Meteo with device-location-first lookup when enabled in Settings, otherwise IP geolocation fallback |
| Daily forecast summary | Implemented | Open-Meteo daily data with a metric-led dashboard presentation and compact 3-day list |
| Weather expand/collapse preference | Implemented | Setting is wired |
| Surf spot settings model | Implemented | Persisted in settings |
| Surf card parity | Bootstrap | Weather now reserves a dedicated surf subsection and surf-spot framing, but marine metrics still need fuller provider parity |

## Travel Alerts

| Feature | Android status | Notes |
| --- | --- | --- |
| Travel alerts card | Implemented | Dashboard card now shows an aggregate status plus dedicated Travel Advisory and Regional Security rows with checking, ready, stale, and unavailable states, compact status pills, and coverage context |
| Smartraveller advisory | Implemented | Uses Smartraveller destination export data with tolerant country-name matching and Level 1/2/3/4 severity mapping |
| ReliefWeb regional security | Implemented | Uses ReliefWeb reports with primary-country plus bordering-country coverage; requires an approved ReliefWeb app name saved in Settings |
| Weather alerts | Deferred | Provider decision still pending |

## Fuel Prices And Emergency Care

| Feature | Android status | Notes |
| --- | --- | --- |
| Fuel card | Implemented | Dashboard card now highlights the cheapest nearby diesel and gasoline stations in a more scan-friendly row layout while keeping the same ready/configuration/unavailable/no-stations states |
| Spain/France/Italy public fuel providers | Implemented | Device-first with public-IP fallback and a fixed 50 km search radius |
| Germany Tankerkonig support | Implemented | Requires a user-supplied Tankerkonig key saved in the app's Settings screen; the key stays encrypted on-device |
| Emergency care card | Implemented | Dashboard card now renders loading, ready, permission-required, configuration-required, unavailable, and error states with a direct Maps handoff when a nearby hospital is resolved |
| Nearby hospitals via Places | Implemented | Uses Google Places Nearby Search (New) with device-location first and public-IP geolocation fallback |
| In-app map preview | Planned | Depends on Maps integration |

## Visited Places And Travel History

| Feature | Android status | Notes |
| --- | --- | --- |
| Visited screen route | Implemented | UI route now leads with the travel footprint and capture controls instead of explanatory copy, then shows the world map, saved places, permission state, and country-day summaries |
| Visited place persistence | Implemented | Room-backed local history captures IP and optional device-location visits during refresh |
| Country-day aggregation | Implemented | Same-day device precedence plus inferred gap filling with yearly/monthly summaries |
| Country-day export | Planned | No Android export flow yet |
| World map rendering | Implemented | Google Maps Compose renders all-time saved-place pins plus selected-year country shading from bundled world-country boundaries; configure a local app-level Maps SDK key via `local.properties`, Gradle properties, or environment variables |

## Time Tracking

| Feature | Android status | Notes |
| --- | --- | --- |
| Time tracking screen route | Implemented | Route now opens with an overview card, stronger active/ready state treatment, and denser project/session hierarchy |
| Project list and settings | Implemented | Add-only local project list with inline creation and project selection before start |
| Ledger persistence | Implemented | Room-backed projects and entries with a single active session invariant |
| Foreground-service runtime | Implemented | Persistent special-use notification runs while tracking and resumes after app relaunch or service recreation |
| Allocation workflow | Implemented | Start/stop tracking per selected project with recent completed sessions shown in-app |
| Reporting/export | Planned | Not implemented yet |

## Build, Release, And Distribution

| Feature | Android status | Notes |
| --- | --- | --- |
| Gradle wrapper project | Implemented | Android Studio-compatible |
| Make targets | Implemented | Build/test/lint/run/release helper targets |
| Wireless ADB helper | Implemented | `make connect-wireless` |
| Emulator screenshot review workflow | Implemented | `make screenshots` renders deterministic fixture screens and exports light and dark theme PNGs to `output/screenshots/android/phone` |
| Signed AAB/APK script flow | Implemented | Requires local signing env only; shipped app credentials are not read from local env files |
| Google Play internal publish script | Implemented | Requires local Play service account |
| Release version bump helper | Implemented | `prepare-release.sh` now bumps tracked version metadata, prepends `CHANGELOG.md`, writes Play release notes, and creates a local release commit plus tag without auto-pushing |
