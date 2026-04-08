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
| Single main dashboard screen | Implemented | Adaptive Compose route is live and the compact dashboard now uses a location-led header with the Nomad symbol mark, visible refresh progress, a weather-first three-up overview strip, and metric-led detail cards without duplicate top navigation |
| Settings screen | Implemented | Settings now use grouped control-room sections, clearer toggle descriptions, and stronger local-first credential messaging |
| About screen | Implemented | About now acts as a real trust and brand surface with stronger product framing, local-first privacy explanation, live app version metadata, and direct links to the website and GitHub project |
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
| Wi-Fi SSID / RSSI | Implemented | Uses `WifiManager`; the dashboard now also shows link speed and Wi-Fi band when Android exposes them |
| VPN detection | Implemented | Uses network transport check |
| Public IP lookup | Implemented | FreeIPAPI remains primary, with a plain-IP fallback path plus copyable dashboard presentation and last-known retention during transient lookup failures |
| IP geolocation | Implemented | FreeIPAPI with tolerant timezone parsing for the provider's current response schema and by-address fallback geolocation when the current-IP endpoint fails |
| Device vs IP location comparison | Implemented | Travel Context now shows the current device place and the public-IP-derived place side by side when available |
| Travel context card | Implemented | Card now compares device and public-IP location, exposes Android-native map actions, and shows denser Wi-Fi travel telemetry instead of only basic region/country fields |

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
| Battery health summary | Implemented | Uses Android battery-health signals when available, with a heuristic fallback |
| Discharge watts estimate | Implemented | Uses current-now and voltage where available |
| Battery history chart | Implemented | Retained Room-backed battery percentage samples from dashboard refreshes now drive the power card history graph |
| Detailed power diagnostics | Implemented | Power now shows an inline status chip plus health, source, temperature, voltage, and power-flow context in the main dashboard card |

## Weather And Surf

| Feature | Android status | Notes |
| --- | --- | --- |
| Current weather | Implemented | Open-Meteo with device-location-first lookup when enabled in Settings, otherwise IP geolocation fallback |
| Hourly weather checkpoints | Implemented | Open-Meteo hourly data now drives real `+3h`, `+6h`, and `+12h` dashboard checkpoints with icons, rain chance, and wind |
| Daily forecast summary | Implemented | Open-Meteo daily data now renders icon-led forecast rows with temperature, rain chance, and wind detail |
| Weather expand/collapse preference | Implemented | Setting is wired |
| Surf spot settings model | Implemented | Persisted in settings with manual name/coordinate editing plus current-location autofill |
| Surf card parity | Implemented | Open-Meteo marine data now powers wave, swell, wind, sea temperature, and near-term checkpoints for the saved surf spot |

## Travel Alerts

| Feature | Android status | Notes |
| --- | --- | --- |
| Travel alerts card | Implemented | Dashboard card now shows an aggregate status in the header row plus dedicated Travel Advisory and Regional Security panels with checking, ready, stale, and unavailable states, compact summaries, and explicit border-country coverage context |
| Smartraveller advisory | Implemented | Uses the live Smartraveller destinations page with tolerant country-name matching and Level 1/2/3/4 severity mapping across the current country plus all bordering countries |
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
| Visited screen route | Implemented | UI route now leads with a local-first travel overview, map-first footprint, denser country-day summaries, and clearer saved-stop rows instead of explanatory filler |
| Visited place persistence | Implemented | Room-backed local history captures IP and optional device-location visits during refresh |
| Country-day aggregation | Implemented | Same-day device precedence plus inferred gap filling with yearly/monthly summaries |
| Country-day export | Planned | No Android export flow yet |
| World map rendering | Implemented | Google Maps Compose renders all-time saved-place pins plus selected-year country shading from bundled world-country boundaries; the camera now opens around the latest relevant region while keeping the selected-year footprint in frame, and visited-country contrast is stronger; configure a local app-level Maps SDK key via `local.properties`, Gradle properties, or environment variables |

## Time Tracking

| Feature | Android status | Notes |
| --- | --- | --- |
| Time tracking screen route | Implemented | Route now leads with a live unallocated buffer, clear running/paused state, quick-allocation chips, and compact project management instead of project-first session start |
| Project list and settings | Implemented | Add-only local project list with inline creation; the built-in `Other` lane is always available for quick allocation |
| Ledger persistence | Implemented | Room-backed projects and entries with a single active session invariant |
| Foreground-service runtime | Implemented | Persistent special-use notification runs while the unallocated buffer is actively capturing and resumes after app relaunch or service recreation |
| Allocation workflow | Implemented | Time is captured into a persistent unallocated buffer first, then assigned from dashboard or tracking-screen quick actions into a chosen project or `Other` |
| Automatic daily tracking window | Implemented | Settings now define a same-day auto-capture window, defaulting to `07:00-19:00`; outside it, manual play/pause still works |
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
