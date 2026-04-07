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
| Visited screen route | Bootstrap | Route exists, map/persistence not complete |
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
| Travel alerts card | Bootstrap | UI contract exists |
| Smartraveller advisory | Planned | Planned provider parity |
| ReliefWeb regional security | Planned | App config path is ready |
| Weather alerts | Deferred | Provider decision still pending |

## Fuel Prices And Emergency Care

| Feature | Android status | Notes |
| --- | --- | --- |
| Fuel card shell | Bootstrap | Setting and dashboard text exist |
| Spain/France/Italy public fuel providers | Planned | Not implemented yet |
| Germany Tankerkonig support | Planned | User-supplied key policy already modeled |
| Emergency care card shell | Bootstrap | Setting and dashboard text exist |
| Nearby hospitals via Places | Planned | Android replacement for Apple Maps |
| In-app map preview | Planned | Depends on Maps integration |

## Visited Places And Travel History

| Feature | Android status | Notes |
| --- | --- | --- |
| Visited screen route | Bootstrap | UI route exists |
| Visited place persistence | Planned | Room entities scaffolded |
| Country-day aggregation | Planned | Room entities scaffolded |
| Country-day export | Planned | No Android export flow yet |
| World map rendering | Planned | Google Maps integration planned |

## Time Tracking

| Feature | Android status | Notes |
| --- | --- | --- |
| Time tracking screen route | Bootstrap | UI route exists |
| Project list and settings | Planned | Model exists but no management UI yet |
| Ledger persistence | Planned | Room entities scaffolded |
| Foreground-service runtime | Planned | Not implemented yet |
| Allocation workflow | Planned | Not implemented yet |
| Reporting/export | Planned | Not implemented yet |

## Build, Release, And Distribution

| Feature | Android status | Notes |
| --- | --- | --- |
| Gradle wrapper project | Implemented | Android Studio-compatible |
| Make targets | Implemented | Build/test/lint/run/release helper targets |
| Wireless ADB helper | Implemented | `make connect-wireless` |
| Signed AAB/APK script flow | Implemented | Requires local signing env |
| Google Play internal publish script | Implemented | Requires local Play service account |
| Release version bump helper | Implemented | `prepare-release.sh` |

