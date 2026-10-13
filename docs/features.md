# Feature Inventory

Last updated: 2026-10-13

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
| Single main dashboard screen | Implemented | The dashboard opens with the macOS glance overview: location-led header with a caption that shortens without cutting the IP part, refresh and overflow menu, a one-line time-tracking row with an Allocate menu, a verdict strip ("Ready to work", "Workable", "Needs attention", "Checking conditions", "You're offline") with the data-usage menu, Here/Home clocks, four tappable vital tiles (Internet, Power, VPN, Weather) that expand and scroll to their card, up to three ranked heads-up items, and Workplaces / Saved essentials links. Cards below collapse to one-line summaries, colour means status only, and turned-off cards collapse into one "Turned off: …" row with a deep link to the exact Settings group |
| First-run location onboarding | Implemented | When Android location permission is still missing, the dashboard now shows a top-of-screen grant card before the main cards so device-aware features are discoverable on fresh installs |
| Settings screen | Implemented | Eight pages (General, Weather & Local, Safety, Stays & Visas, Connection & Data, Work, Privacy & Location, Advanced; the Schengen counter, stay allowances and tax residence days live on Stays & Visas) as a page list on phones and list plus page side by side on wide layouts; every group carries its own short privacy footer; `settings?destination=` deep links open the page, scroll to the group and highlight it; Appearance now applies to the app theme |
| About screen | Implemented | About now acts as a real trust and brand surface with stronger product framing, local-first privacy explanation, live app version metadata, and direct links to the website and GitHub project |
| Visited screen route | Implemented | Route now opens with a capture/control overview card, then the world map, local history summaries, saved places, and country-day breakdowns |
| Time tracking route | Implemented | Route now shares the same product language as the rest of the app, with clearer overview, active-state, and recent-session sections |
| Adaptive phone/tablet navigation | Implemented | Bottom bar on compact, rail on wider layouts |
| Companion workspace | Implemented | Dashboard Work Readiness opens local offline essentials, an explicit workplace diary, timezone planners, quiet foreground-only alerts, and versioned backup tools |
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
| IP geolocation | Implemented | FreeIPAPI with tolerant timezone parsing for the provider's current response schema, DataStore default repair for fresh empty installs, and by-address fallback geolocation through ipify's JSON endpoint when the current-IP endpoint fails |
| Device vs IP location comparison | Implemented | Travel Context now shows the current device place and the public-IP-derived place side by side when available |
| Travel context card | Implemented | Card now compares device and public-IP location, exposes Android-native map actions, shows denser Wi-Fi travel telemetry, keeps a startup `checking device location` state instead of flashing immediate unavailable copy during cold launch, and can promote from IP fallback to device context later in the same refresh |

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
| Current weather | Implemented | Open-Meteo now restores the last-successful cached weather snapshot on cold launch, refreshes in place with a small card-level progress indicator, and still prefers device coordinates before falling back to IP geolocation |
| Hourly weather checkpoints | Implemented | Open-Meteo hourly data now drives real `+3h`, `+6h`, and `+12h` dashboard checkpoints with icons, rain chance, and wind |
| Daily forecast summary | Implemented | Open-Meteo daily data now renders icon-led forecast rows with temperature, rain chance, and wind detail |
| Weather expand/collapse preference | Implemented | Setting is wired |
| Surf spot settings model | Implemented | Persisted in settings with manual name/coordinate editing plus current-location autofill |
| Surf card parity | Implemented | Open-Meteo marine data now powers wave, swell, wind, sea temperature, and near-term checkpoints for the saved surf spot |

## Travel Alerts

| Feature | Android status | Notes |
| --- | --- | --- |
| Travel alerts card | Implemented | Dashboard card now shows an aggregate status in the header row plus dedicated Travel Advisory and Regional Security panels with checking, ready, stale, and unavailable states, compact summaries, explicit border-country coverage context, a direct `More details` action when a source page is available, cached-content-in-place refresh behavior, and automatic transient retries before falling back to stale/unavailable state |
| Smartraveller advisory | Implemented | Uses the live Smartraveller destinations page first, falls back to the legacy `destinations-export` feed, and then retries through a hidden WebView fetch on Android; tolerant country-name matching and Level 1/2/3/4 severity mapping still cover the current country plus all bordering countries, the compact dashboard summary now mirrors macOS by staying short and stable, and numeric apostrophe entities such as `&#039;` decode correctly in advisory detail text |
| ReliefWeb regional security | Implemented | Uses ReliefWeb reports with primary-country plus bordering-country coverage; requires an approved ReliefWeb app name saved in Settings |
| Weather alerts | Deferred | Provider decision still pending |

## Fuel Prices And Emergency Care

| Feature | Android status | Notes |
| --- | --- | --- |
| Fuel card | Implemented | Dashboard card now highlights the cheapest nearby diesel and gasoline stations in a more scan-friendly row layout while keeping the same ready/configuration/unavailable/no-stations states, restoring the last-successful cached card on cold start, and refreshing in place before or after device-to-IP location fallback settles |
| Local Info card | Implemented | Optional dashboard card now combines resolved location context, Nager.Date public holidays, best-effort OpenHolidays school holidays when a confident subdivision match exists, and the existing Eurostat/HUD USER price rows; startup now restores cached content first, then refreshes in place while waiting for device place resolution or IP fallback |
| Spain/France/Italy public fuel providers | Implemented | Device-first with public-IP fallback and a fixed 50 km search radius |
| Germany Tankerkonig support | Implemented | Requires a user-supplied Tankerkonig key saved in the app's Settings screen; the key stays encrypted on-device |
| Emergency care card | Implemented | Subtitle "Within 25 km · Emergency 112" (radius reflects any wider fallback search), up to three hospitals nearest first with neutral Public/Private chips, map preview per row, loading/permission/configuration/unavailable/error states, cached content on cold launch and automatic transient retries |
| Nearby hospitals via Places | Implemented | Uses Google Places Nearby Search (New) with device-location first and public-IP geolocation fallback |
| In-app map preview | Implemented | Fuel and Emergency Care rows open a bottom-sheet Google Map preview when a Maps SDK key is configured, otherwise the external maps app; Google Maps sits behind a ⋯ menu |

## Visited Places And Travel History

| Feature | Android status | Notes |
| --- | --- | --- |
| Visited screen route | Implemented | UI route now leads with a local-first travel overview, map mode switch, map-first footprint/path display, denser country-day summaries, chronological travel log, clear-history action, and clearer saved-stop rows |
| Visited place persistence | Implemented | Room-backed local history captures aggregate saved places plus a separate chronological visited-place event table; same-place same-day captures merge into one event while same-place different-day captures remain distinct |
| Visited source priority | Implemented | Visited places, country days, and travel-path events prefer resolved device location; public-IP geolocation is only stored for a capture when device location is unavailable or unresolved |
| Country-day aggregation | Implemented | Same-day device precedence plus inferred gap filling with yearly/monthly summaries; aggregate compatibility is preserved while event history powers path display |
| Travel path history | Implemented | Travel Path mode shows selected-year ordered stops, a route line between coordinate-backed stops, and a chronological log with date/date range, source, and stop day count |
| Country-day export | Implemented | Shares a macOS v1-compatible CSV with country-day provenance and capture-source columns, plus a readable summary; Android JSON is a native travel-ledger backup rather than a cross-platform format promise |
| Travel-ledger JSON backup | Implemented | User-initiated share, document save, preview, and additive import cover places, chronological events, country days, and stay reminders; conflicts do not overwrite local rows |
| World map rendering | Implemented | Google Maps Compose renders all-time saved-place pins plus selected-year country shading from bundled world-country boundaries in World Footprint mode; Travel Path mode renders numbered selected-year stops and route polylines; configure a local app-level Maps SDK key via `local.properties`, Gradle properties, or environment variables |

## Time Tracking

| Feature | Android status | Notes |
| --- | --- | --- |
| Time tracking screen route | Implemented | Route now leads with a live unallocated buffer, clear running/paused state, quick-allocation chips, and compact project management instead of project-first session start |
| Project list and settings | Implemented | Add-only local project list with inline creation; the built-in `Other` lane is always available for quick allocation |
| Ledger persistence | Implemented | Room-backed projects and entries with a single active session invariant |
| Foreground-service runtime | Implemented | Persistent special-use notification with the system chronometer runs while the unallocated buffer is actively capturing and resumes after app relaunch or service recreation |
| Allocation workflow | Implemented | Time is captured into a persistent unallocated buffer first, then assigned from dashboard or tracking-screen quick actions into a chosen project or `Other` |
| Automatic daily tracking window | Implemented | Settings now define a same-day auto-capture window, defaulting to `07:00-19:00`; outside it, manual play/pause still works |
| Interruption tracking | Implemented | Dashboard and tracking screens now expose a dedicated interruption action with a same-day counter, red flash, and a `23 minute` cooldown fade back toward the default warm tone |
| Focus-loss reporting | Implemented | Local reports now estimate `interruptions x 23 minutes`, show same-day totals, and break allocated work down by project with estimated focused time |
| Reporting/export | Implemented | User-initiated Sharesheet actions provide a credential-free plain-text focus report, completed-entry CSV, and versioned JSON backup; imports preview additions/conflicts and never import an active capture |

## Companion

| Feature | Android status | Notes |
| --- | --- | --- |
| Offline essentials | Implemented | User-created addresses, contacts, notes, and allowed Maps/geo links remain in the local Companion document. A selectable safety block sits above them: the emergency numbers for the current country and the user's embassy (nearest listed mission and consular line), both from datasets bundled with the app so they work offline; unverified countries show nothing |
| Workplace diary | Implemented | Disabled by default. A row is stored only after the user enables the diary, provides a place label, and taps **Save current observation**; it never automatically identifies stationary locations or cafés as workplaces |
| Workplace data minimization | Implemented | Connection names and location-derived Maps links each require their own opt-in; saved labels remain editable and IP-derived map links are marked approximate |
| Timezone planner | Implemented | Local IANA-zone overlap calculation accounts for DST transitions without provider calls |
| Quiet contextual notifications | Implemented | Permission-gated, low-priority alerts react only to sustained foreground-observed connection, VPN, or charging transitions and honour configured quiet hours and cooldown |
| Companion backup | Implemented | Versioned JSON export, bounded import preview, and additive merge retain local conflicts and exclude credentials and dashboard history |

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
| Release shrinking | Implemented | Release builds enable R8 minification and Android resource shrinking; Retrofit annotation metadata and serialization-generated serializers are retained by the release rules |
| Benchmark and Baseline Profile source | Implemented | The connected-device benchmark module contains cold-start and dashboard-scroll Macrobenchmark scenarios plus Baseline Profile collection source; no measurement result is recorded here |

## Your Embassy and Stays & Visas cards

| Feature | Android status | Notes |
| --- | --- | --- |
| Your Embassy card | Implemented | Per citizenship: the foreign ministry's consular line (selectable, copyable, tap to dial; "24/7 consular line" only where the ministry says so) and up to three nearest listed missions with kind, city and distance ("~4 km" when approximate; an IP-only position is always approximate). A mission row opens the maps app; its menu has Show in Maps, Open website and Copy address. Trip-registration and mission-list links, EU/Nordic fallback notice, at-home and location-needed states, dataset footer. Without a citizenship the card is turned off and, in Customize Layout, prompts for Citizenship. Everything is computed on the device from bundled data |
| Stays & Visas card | Implemented | Current stay, per-country allowance (remaining days, leave-by, overstay), Schengen 90/180 (used, leave-by, exempt and unknown-citizenship states), tax-year days against the threshold ("not tax or legal advice") and a link to the Stays & Visas settings page. Turned off with a prompt for Visited places while visited places are off. "Review days" opens the visited map |

## macOS 0.17-0.18 parity (October 2026)

Ported from the macOS feature brief in `docs/macos-parity-brief-2026-10-12.md`, updated to macOS v0.18.0. Rules, thresholds and wording live in `core:travel` and match macOS.

| Feature | Android status | Notes |
| --- | --- | --- |
| Heads-up engine | Implemented | At most three items, one per kind, ranked by severity then the travel profile's tie-break order; re-evaluated every 60 s. Kinds: travel advisory, regional security, strong wind, rain soon, UV, air quality, public holiday, local rhythm, cheapest fuel, daylight (Road trip & RV only), stay allowance, Schengen limit, tax residence days. Data cap and weather alert never fire on Android (no Data Guard, no weather-alert provider) |
| Travel profiles | Implemented | Nomad and Road trip & RV set the recommended card order and heads-up ranking; switch from Settings › General or the dashboard overflow menu. A customised order is kept and Settings offers "Use recommended order" |
| Compact, collapsible cards | Implemented | Every card folds to a ~40dp line (title · summary · badge · chevron) with saved state; Customize Layout (overflow menu) adds drag/move/width controls; half-width cards pair on wide layouts; one in-card action style; sparklines replace the old charts; units drawn smaller than numbers |
| Emergency numbers | Implemented | Bundled table for 76 countries (the brief's "77" was a miscount; macOS ships 76), incl. Morocco (19 / 15 / Gendarmerie 177) and rural police (ES 062, BR 191, TH 1193); first Local Info row, Emergency Care subtitle, Saved essentials, arrival briefing |
| Emergency Care hospitals | Implemented | Google Places within 25 km, nearest public and nearest private guaranteed, shown nearest first with neutral Public/Private chips; widens to 50 then 100 km, and runs five text searches at 100 km only if nothing was found (at most 8 Places requests, usually 1); in-app map preview with Google Maps behind a ⋯ menu |
| Your Embassy | Implemented | See the Your Embassy card row above; bundled Wikidata missions (10,181) and 28 verified consular lines |
| Daylight | Implemented | Sunrise/sunset/civil twilight computed on the device (NOAA/Meeus); Weather card line in every profile; before-dark heads-up in Road trip & RV |
| Stays and visa days | Implemented | Schengen 90/180 (29 members as of Oct 2026), per-country stay allowance, tax-year days; Stays & Visas card and settings page |
| Arrival briefing | Implemented | One-time "Welcome to …" card for up to three days after a border crossing (local time vs home, emergency numbers, embassy, driving side, plugs, currency, holiday, advisory, local rhythm); "Got it" remembers the country; reopen from the overflow menu. No Data Guard tip on Android |
| Local rhythm | Implemented | Ramadan, Eids, Lunar New Year, Holy Week, Yom Kippur, Songkran, Golden Week, Ferragosto, Nyepi, Diwali with visitor guidance, offline |
| UV and air quality | Implemented | UV from Open-Meteo weather, WHO bands; European AQI from the Open-Meteo Air Quality API with coordinates rounded to ~11 km, only while device-location weather and air quality are on |
| Settings structure | Implemented | Eight pages with deep links (see Settings screen row) |
| Quick Settings tile | Implemented | "Travel status" tile whose subtitle is the macOS menu-bar status line (battery ≤ 50%, offline, optional top heads-up, latency, temperature); Android's equivalent of the menu bar text |
| Home-screen widgets | Implemented | Glance, Safety and Daylight Glance widgets from a privacy-minimal local snapshot (no IP, coordinates or citizenship codes), "Updated" footer and 6 h stale state; taps deep-link to the related card |
| Shortcuts and deep links | Implemented | `nomaddashboard://dashboard/<card>` and `nomaddashboard://settings/<destination>`; five launcher shortcuts mirroring the macOS App Intents (emergency number, dashboard status, nearest embassy, daylight left, switch travel profile). Spoken Siri-style answers are not ported; the shortcuts open the card that holds the answer |
| Data Guard | Deferred | Not available on Android; the glance shows a VPN tile instead and data-cap heads-up items never fire |
