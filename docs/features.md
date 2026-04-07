# Feature Inventory

Last updated: 2026-04-07

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
| Single main dashboard screen | Implemented | Adaptive Compose route is live |
| Settings screen | Implemented | Settings persist via Proto DataStore |
| About screen | Implemented | Minimal bootstrap content |
| Visited screen route | Implemented | Route shows the visited world map, local history summaries, saved places, and country-day breakdowns |
| Time tracking route | Bootstrap | Route exists, runtime not complete |
| Adaptive phone/tablet navigation | Implemented | Bottom bar on compact, rail on wider layouts |
| Nomad visual identity | Implemented | Warm sand / teal / coral card-based UI |

## Connectivity And Travel Context

| Feature | Android status | Notes |
| --- | --- | --- |
| Internet reachability summary | Implemented | Uses `ConnectivityManager` |
| Latency probe | Implemented | Socket connect probe |
| Passive throughput estimate | Implemented | Based on `TrafficStats` delta |
| Wi-Fi SSID / RSSI | Implemented | Uses `WifiManager`; current API is basic |
| VPN detection | Implemented | Uses network transport check |
| Public IP lookup | Implemented | FreeIPAPI |
| IP geolocation | Implemented | FreeIPAPI |
| Travel context card | Implemented | Uses current bootstrap geolocation data |

## Power

| Feature | Android status | Notes |
| --- | --- | --- |
| Battery percentage | Implemented | From battery intent |
| Charging state | Implemented | From battery intent |
| Battery health summary | Implemented | Heuristic bootstrap summary |
| Discharge watts estimate | Implemented | Uses current-now and voltage where available |
| Detailed power diagnostics | Planned | Not yet at macOS richness |

## Weather And Surf

| Feature | Android status | Notes |
| --- | --- | --- |
| Current weather | Implemented | Open-Meteo |
| Daily forecast summary | Implemented | Open-Meteo daily data |
| Weather expand/collapse preference | Implemented | Setting is wired |
| Surf spot settings model | Implemented | Persisted in settings |
| Surf card parity | Bootstrap | Data model and settings are present; dedicated UI is next |

## Travel Alerts

| Feature | Android status | Notes |
| --- | --- | --- |
| Travel alerts card | Implemented | Dashboard card now shows an aggregate status plus dedicated Travel Advisory and Regional Security rows with checking, ready, stale, and unavailable states |
| Smartraveller advisory | Implemented | Uses Smartraveller destination export data with tolerant country-name matching and Level 1/2/3/4 severity mapping |
| ReliefWeb regional security | Implemented | Uses ReliefWeb reports with primary-country plus bordering-country coverage; requires an approved ReliefWeb app name saved in Settings |
| Weather alerts | Deferred | Provider decision still pending |

## Fuel Prices And Emergency Care

| Feature | Android status | Notes |
| --- | --- | --- |
| Fuel card | Implemented | Dashboard card now shows ready/configuration/unavailable/no-stations states with the cheapest nearby diesel and gasoline rows |
| Spain/France/Italy public fuel providers | Implemented | Device-first with public-IP fallback and a fixed 50 km search radius |
| Germany Tankerkonig support | Implemented | Requires a user-supplied Tankerkonig key saved in the app's Settings screen; the key stays encrypted on-device |
| Emergency care card shell | Bootstrap | Setting and dashboard text exist |
| Nearby hospitals via Places | Planned | Android replacement for Apple Maps |
| In-app map preview | Planned | Depends on Maps integration |

## Visited Places And Travel History

| Feature | Android status | Notes |
| --- | --- | --- |
| Visited screen route | Implemented | UI route shows the visited world map, saved places, permission state, and country-day summaries |
| Visited place persistence | Implemented | Room-backed local history captures IP and optional device-location visits during refresh |
| Country-day aggregation | Implemented | Same-day device precedence plus inferred gap filling with yearly/monthly summaries |
| Country-day export | Planned | No Android export flow yet |
| World map rendering | Implemented with config blocker | Google Maps Compose renders all-time saved-place pins plus selected-year country shading from bundled world-country boundaries, but Android Maps SDK still requires an app-level manifest API key and is not currently configurable from the in-app Settings flow |

## Time Tracking

| Feature | Android status | Notes |
| --- | --- | --- |
| Time tracking screen route | Implemented | Route now shows disabled guidance, local projects, active session state, and recent entries |
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
| Emulator screenshot review workflow | Implemented | `make screenshots` renders deterministic fixture screens and exports PNGs to `output/screenshots/android/phone` |
| Signed AAB/APK script flow | Implemented | Requires local signing env only; shipped app credentials are not read from local env files |
| Google Play internal publish script | Implemented | Requires local Play service account |
| Release version bump helper | Implemented | `prepare-release.sh` now bumps tracked version metadata, prepends `CHANGELOG.md`, writes Play release notes, and creates a local release commit plus tag without auto-pushing |
