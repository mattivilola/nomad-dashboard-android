# Status

Last updated: 2026-04-23

## Overall

Current phase: `Foundation + Core bootstrap complete`

Current repository state:
- initial Android port scaffold implemented
- initial Android scaffold committed
- documentation and wireless helper follow-up committed
- prior build/test/lint baseline verified locally
- real-device debug install verified on Android phone via wireless debugging
- physical-device-first `make run` targeting added for mixed phone + emulator setups
- fuel provider implementations and dashboard fuel card wiring implemented
- visited places persistence and country-day aggregation implemented
- visited screen history UI implemented
- visited device-location opt-in and permission CTA implemented
- visited map rendering implemented with Google Maps Compose, all-time place
  pins, and selected-year country shading
- time-tracking persistence, screen state, and foreground runtime implemented
- Compose UI smoke test suite added for the Android shell
- emulator-first connected-test workflow added
- encrypted device-local provider credential storage implemented for Germany
  Tankerkonig support
- travel-alert provider integrations implemented for Smartraveller advisory and
  ReliefWeb regional security
- ReliefWeb app name handling moved into encrypted device-local provider
  credentials and the Settings screen
- dashboard travel-alert card now renders aggregate, advisory, and regional
  security states from live provider-backed repository data
- emergency-care provider integration implemented with Google Places Nearby
  Search (New), device-location-first and public-IP fallback lookup, dashboard
  card state wiring, and open-in-maps handoff for resolved hospitals
- Local Info dashboard feature implemented with device-first/IP-fallback
  location context, Nager.Date public holidays, best-effort OpenHolidays
  school-holiday matching, reused Eurostat/HUD USER price rows, response
  caching, settings migration, and dashboard card UI
- Android-versus-macOS screenshot review completed; the highest-value next
  parity work is dashboard and visited UX refinement rather than new visual
  theming alone
- dashboard UX parity pass started: compact quick actions, denser top-level
  summary strip, weather-first default ordering, and metric-led detail cards
  are now implemented in the dashboard UI layer
- dashboard header polish landed: duplicate top navigation and settings access
  are removed from the phone dashboard, the header now uses concrete location
  and freshness language, refresh shows visible progress, and the overview
  strip now leads with weather instead of a vague overall-status tile
- dashboard brand polish landed: the Nomad symbol mark now sits inline with
  the dashboard title so the screen opens with clearer product identity without
  consuming the right-side action area
- connectivity dashboard refinement landed: the card header now carries the
  live status chip, duplicate online copy is removed, empty throughput values
  render as `0 Mbps`, and retained Room-backed throughput/latency samples now
  drive real mini-charts
- dashboard travel-context redesign landed: public IP parsing now tolerates the
  provider's current timezone schema, the card compares device and public-IP
  location side by side, richer Wi-Fi telemetry is visible, and map actions
  now use Android-native intents instead of the previous dead URI path
- Android public-IP reliability pass landed: the client now sends explicit JSON
  accept plus app identity headers, accepts `timeZones` as either a string or
  an array, and falls back through a plain-IP lookup plus by-address
  geolocation when the current-IP endpoint fails
- travel alerts reliability and UX pass landed: Smartraveller advisory now
  tries the live destinations page first, falls back to the legacy
  `destinations-export` feed, and finally uses a hidden WebView fetch on
  Android when Smartraveller stalls direct client requests; coverage remains
  `current country + bordering countries` for cases like France plus 8
  neighbors, and the card now keeps its overall status chip in the top row
  with denser per-signal panels
- travel alerts detail pass landed: the Smartraveller row now keeps the
  existing severity/coverage logic but also shows the concise advice-reason
  sentence when the destination page exposes one, plus a direct `More details`
  action to open the source page from the dashboard
- whole-app UX parity pass expanded across settings, about, and time tracking;
  shared badges, metric blocks, chart shells, and stronger section headers now
  give the app a more coherent product language
- time-tracking model redesign landed: the app now captures into a persistent
  unallocated buffer first, shows the live counter on both dashboard and
  tracking screens, exposes quick-allocation project chips plus a built-in
  `Other` lane, and adds a configurable same-day auto-capture window
- interruption tracking landed inside time tracking: the dashboard and
  tracking screens now expose a dedicated interruption action with daily count,
  red-flash feedback, and a `23 minute` cooldown fade, while local reports now
  estimate focus loss per day and per allocated project
- about-screen refinement landed: the screen now acts as a clearer trust
  surface with direct website and GitHub actions, live version metadata, and a
  proper maintainer/distributor footer instead of generic filler copy
- power dashboard refinement landed: the card header now carries its live
  status chip inline, device-backed health/source/thermal diagnostics now
  replace the vague state copy, and retained Room-backed battery percentage
  samples now drive a real local history chart
- weather refresh now honors the existing device-location weather setting and
  falls back to IP geolocation when device coordinates are unavailable
- startup location bootstrap hardening landed: dashboard refresh is now
  single-flight, device coordinates survive reverse-geocoder misses, and
  location-based cards show a shared `checking device location` state before
  falling back to IP context on cold start
- dashboard startup warm-start and section-cache pass landed: the repository
  now restores last-successful location-dependent dashboard sections from
  Room-backed section cache storage, keeps cached card content visible during
  refresh, and adds per-card refresh indicators for weather, travel alerts,
  local info, fuel prices, and emergency care
- startup location orchestration now runs device and IP resolution in the same
  refresh cycle, prefers device context when it resolves first, falls back to
  IP context when needed, and can promote location-dependent cards from IP
  fallback to device context later in the same refresh
- dashboard refresh retry hardening landed: IP lookup, weather, travel alerts,
  local info, fuel, and emergency care now use shared transient retry logic,
  and the repository no longer aborts the full refresh when one provider fails
- weather and surf refinement landed: the dashboard now uses real Open-Meteo
  hourly checkpoints, icon-led forecast rows with rain and wind detail, and a
  live marine surf subsection for the saved surf spot instead of placeholder
  copy
- surf-spot setup is now practical on Android: Settings expose manual surf
  spot name plus coordinate fields and a current-location autofill action
- visited UX parity pass started: the screen now opens with an operational
  overview card and keeps capture guidance behind the map/history content
- visited map framing and hierarchy refinement landed: the world footprint now
  opens around the latest relevant region, keeps the selected-year footprint in
  frame, uses stronger visited-country contrast, and presents country/saved-stop
  summaries in a denser travel-dashboard layout
- screenshot review workflow now captures both light and dark theme exports for
  each review screen
- `make run` now prints timestamped install/launch progress updates and waits
  for `am start -W` launch timing so slow wireless-device deploys are less
  opaque
- dashboard first-run location onboarding landed: when Android location access
  is still missing, the dashboard now shows a prominent top-of-screen grant
  card ahead of the main cards so release installs do not hide the permission
  path below the fold
- dashboard header parity now mirrors the recent macOS travel-context behavior:
  the top header shows device and public-IP locations side by side when they
  resolve, keeps source icon/label differences visible at a glance, and falls
  back to the existing location line when neither source is available
- travel-alert compact-copy parity now mirrors macOS: the advisory summary
  stays concise and stable in the compact/top dashboard presentation, the
  fuller Smartraveller explanation remains in the detail body, and numeric
  apostrophe entities such as `&#039;` decode correctly

## Completed

- Android Studio-compatible multi-module project created
- Gradle wrapper and version catalog created
- Makefile and helper scripts created
- Android Studio JDK auto-selection wired into scripts
- `make connect-wireless` helper added
- Compose app shell implemented
- adaptive navigation implemented
- Nomad theme and shared card components implemented
- Proto DataStore settings implemented
- Room database scaffold implemented
- repository and DI scaffold implemented
- live connectivity snapshot bootstrap implemented
- live power snapshot bootstrap implemented
- live public IP / IP geolocation bootstrap implemented
- live Open-Meteo weather bootstrap implemented
- dashboard route implemented
- weather location sourcing now respects the weather settings toggle and no
  longer depends only on IP geolocation
- weather card now renders real `+3h`, `+6h`, and `+12h` checkpoints from
  Open-Meteo hourly data plus icon-led forecast rows with rain and wind
- Open-Meteo marine surf support implemented for the saved surf spot with
  wave, swell, wind, sea temperature, and near-term checkpoints
- Settings now support manual surf-spot editing and current-location autofill
- settings route implemented
- about route implemented
- dashboard fuel card implemented with device-first / IP-fallback lookup
- dashboard emergency-care card implemented with loading, ready,
  permission-required, configuration-required, unavailable, and error states
- Local Info card implemented with location context, holiday rows, and reused
  official-source Europe/US price signals
- Spain ministry fuel provider implemented
- France government fuel provider implemented
- Italy MIMIT fuel provider implemented
- Germany Tankerkonig fuel provider implemented
- Germany Tankerkonig key moved to encrypted in-app settings storage
- Google Places Nearby Search (New) emergency-care provider implemented with
  local manifest-key configuration checks and hospital-only lookup
- visited route implemented
- visited places persistence implemented
- visited map rendering implemented
- country-day aggregation logic implemented
- time-tracking route implemented with local project creation, start/stop flow, and recent-session history
- time-tracking route redesigned around continuous unallocated capture,
  dashboard quick allocation, and configurable daily auto-run hours
- time-tracking Room persistence implemented for projects and entries
- time-tracking foreground service and persistent notification implemented
- time-tracking interruption persistence, reporting, and focus-loss metrics
  implemented
- time-tracking repository/storage/runtime tests implemented
- Compose UI smoke tests added for app launch, top-level navigation, dashboard shell render, and one settings persistence flow
- emulator boot and emulator-targeted test helper scripts added
- deterministic emulator screenshot review workflow added with fixture-driven
  `make screenshots` exports to `output/screenshots/android/phone`
- physical-device-first `make run` device selection added with `ANDROID_SERIAL` override support
- release/signing/publish helper scripts scaffolded
- tracked changelog and Google Play release-notes generation added to the local release bump flow
- README and docs set added
- connectivity throughput/latency history retention and dashboard mini-charts
  implemented
- retained battery percentage history and the redesigned power dashboard card
  implemented
- travel-context comparison UI implemented for public-IP plus device location,
  copyable public IP, richer Wi-Fi detail, and Android-native map launches

## In Progress

- dashboard UX parity pass is underway; the first implementation landed for the
  compact phone dashboard, but screenshot verification is still pending
- dashboard header / compact travel-alert screenshot verification is still
  pending because the local emulator exits before it appears in `adb devices`
- travel-context physical-device smoke verification is pending for public-IP
  refresh, copy-IP, location permission CTA, and map handoff behavior
- visited UX parity pass is underway; the first implementation landed for a
  map-first screen structure, and the map framing/highlight pass is now in, but
  screenshot verification and final polish are still pending
- settings/about/time-tracking UX parity pass is underway; the new productized
  layouts compile conceptually, but still need screenshot review and a clean
  repo-wide build after the unrelated `core:network` compiler failure is fixed
- physical-device notification smoke verification for the new time-tracking runtime
- emulator-backed screenshot verification remains blocked intermittently by the
  local `Pixel_5_API_31` emulator exiting before it appears in `adb devices`

## Not Started

- time-tracking export parity
- analytics/privacy parity implementation

## Verification Status

Verified:
- `make build`
- `make lint`
- wireless ADB pairing and reconnect flow
- debug APK install on physical Android phone
- app launch on physical Android phone
- ADB target selection now prefers a connected physical device before any booted emulator
- visited model/store/repository unit coverage added and passing via `testDebugUnitTest`
- targeted compile verification for `:core:data` and `:feature:dashboard`
- direct `:app:assembleDebug` verification passed with
  `run_gradle -Pksp.incremental=false`
- travel-alert provider/resolver/repository unit coverage added and passing via
  `:core:data:testDebugUnitTest`
- dashboard travel-alert card Android test added and passing via
  `:feature:dashboard:connectedDebugAndroidTest`
- emergency-care provider/repository coverage now passes inside
  `:core:data:testDebugUnitTest`
- dashboard emergency-care card Android test added and passing via
  `:feature:dashboard:connectedDebugAndroidTest`
- Smartraveller detail-summary coverage plus dashboard Android-test compile
  passed on 2026-04-09 via
  `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertProvidersTest' :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
- dashboard header location-comparison coverage, compact advisory-summary
  coverage, and dashboard Android-test compile passed on 2026-04-15 via
  `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertProvidersTest' :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
- time-tracking repository/storage/runtime coverage added and passing
- `make build` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `make lint` passed again on 2026-04-07 after the encrypted credential
  storage and settings hardening slice
- `run_gradle -Pksp.incremental=false lintDebug` passed on 2026-04-07 after
  the travel-alert slice
- `run_gradle :core:model:compileDebugKotlin :feature:dashboard:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the first dashboard UX parity refactor
- `run_gradle :feature:visited:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the first visited UX parity refactor
- `run_gradle :feature:visited:testDebugUnitTest :feature:visited:compileDebugKotlin :feature:visited:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the visited map framing and UI refinement slice
- `run_gradle :core:designsystem:compileDebugKotlin :feature:dashboard:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:about:compileDebugKotlin :feature:timetracking:compileDebugKotlin :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-07 after the shared UX foundation and whole-app screen redesign pass
- `run_gradle :app:assembleDebug :app:compileDebugAndroidTestKotlin :feature:dashboard:compileDebugAndroidTestKotlin :feature:visited:compileDebugAndroidTestKotlin :feature:timetracking:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-07 after adding the new dashboard, visited, and time-tracking UI checks
- `run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the connectivity history and mini-chart slice
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the travel-context comparison redesign, IP model
  hardening, and Android-native map handoff fix
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh falls back to ipify plus freeip by-address lookup when current lookup fails' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.free ip model decodes string timeZones' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh keeps last known ip context when lookup fails' -Pksp.incremental=false`
  passed on 2026-04-08 after the Android public-IP fallback and schema
  tolerance hardening pass
- `run_gradle :core:data:testDebugUnitTest --tests com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertProvidersTest --tests com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh\ resolves\ travel\ alerts\ and\ prefers\ device\ country\ coverage :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the Smartraveller live-page parsing fix and the
  Travel Alerts dashboard card refinement
- `make build` passed on 2026-04-08 after the dashboard header and compact
  overview polish pass
- `make build` passed on 2026-04-08 after the power telemetry/history and
  dashboard UX refinement slice
- `make build` passed on 2026-04-08 after the weather hourly + surf parity
  slice
- `source scripts/android-env.sh && run_gradle :feature:timetracking:compileDebugKotlin :feature:dashboard:compileDebugKotlin :app:compileDebugKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after adding interruption reporting, cooldown visuals,
  and the new dashboard plus tracking-screen report UI
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.timetracking.RoomTimeTrackingRepositoryTest' :app:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.feature.timetracking.runtime.TimeTrackingForegroundServiceRuntimeTest' :feature:timetracking:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after adding the interruption Room table, repository
  aggregation, and updated time-tracking UI contract
- `make build` passed on 2026-04-08 after the interruption tracking and focus
  reporting slice
- `source scripts/android-env.sh && run_gradle :core:model:compileDebugKotlin :core:datastore:compileDebugKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after adding the new time-tracking auto-window settings
  fields and model mapping
- `make lint` passed on 2026-04-08 after the dashboard header and compact
  overview polish pass
- `make lint` passed on 2026-04-08 after the weather hourly + surf parity
  slice
- `run_gradle :feature:about:compileDebugKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the about-screen trust-surface redesign
- `source scripts/android-env.sh && run_gradle :core:model:compileDebugKotlin :core:datastore:compileDebugKotlin :core:database:compileDebugKotlin :core:network:compileDebugKotlin :core:data:compileDebugKotlin :feature:settings:compileDebugKotlin :feature:dashboard:compileDebugKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after the Local Price Level feature slice
- `source scripts/android-env.sh && run_gradle :core:model:compileDebugKotlin :core:datastore:compileDebugKotlin :core:database:compileDebugKotlin :core:network:compileDebugKotlin :core:data:compileDebugKotlin :feature:dashboard:compileDebugKotlin :feature:settings:compileDebugKotlin -Pksp.incremental=false`
  passed on 2026-04-09 after replacing Local Price Level with Local Info,
  holiday caching, subdivision matching, settings migration, and dashboard UI wiring
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.localprice.DefaultLocalPriceLevelProviderTest' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh resolves local price level from ip country context in europe' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price configuration required in us without token' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh returns us local price row when token and current location exist' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price location required with no country context' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price unsupported for unsupported country' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.update provider credentials clears us local price cache when hud token changes' -Pksp.incremental=false`
  passed on 2026-04-08 after the Local Price Level provider and repository coverage landed
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.localinfo.DefaultLocalInfoProviderTest' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh resolves local price level from ip country context in europe' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price configuration required in us without token' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh returns us local price row when token and current location exist' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price location required with no country context' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh marks local price unsupported for unsupported country' --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.update provider credentials clears us local price cache when hud token changes' -Pksp.incremental=false`
  passed on 2026-04-09 after adding Local Info provider coverage for
  subdivision matching, IP fallback, timezone-aware holiday logic, year
  rollover, cache reuse, and repository wiring
- `source scripts/android-env.sh && run_gradle :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after adding the Local Price Level dashboard Android tests
- `source scripts/android-env.sh && run_gradle :feature:dashboard:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-09 after updating the Local Info dashboard Android test
  contract and debug screenshot fixtures
- `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest' -Pksp.incremental=false`
  passed on 2026-04-09 after adding startup-location bootstrap coverage for
  coordinates-only device fixes, startup checking state, IP fallback, and
  single-flight refresh behavior
- `source scripts/android-env.sh && ANDROID_SERIAL=emulator-5554 run_gradle :feature:dashboard:connectedDebugAndroidTest -Pksp.incremental=false`
  passed on 2026-04-09 after updating the dashboard UI tests for the startup
  location waiting state
- `source scripts/android-env.sh && run_gradle :app:assembleDebug -Pksp.incremental=false`
  passed on 2026-04-09 after the startup location bootstrap hardening slice
- physical-device cold restart smoke verification passed on 2026-04-09 via
  `adb -s 9fb1404 shell am force-stop com.iloapps.nomaddashboard.dev` plus
  `adb -s 9fb1404 shell am start -W -n com.iloapps.nomaddashboard.dev/com.iloapps.nomaddashboard.MainActivity`
- `source scripts/android-env.sh && run_gradle :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  passed on 2026-04-08 after wiring the Local Price Level settings navigation
  path and updating the debug screenshot fixtures
- `:feature:dashboard:connectedDebugAndroidTest` passed on 2026-04-08 during
  the `make test` lane after the dashboard header and compact overview polish
- `make build` passed on 2026-04-07 after setting `ksp.incremental=false` in
  repo-level Gradle properties
- `make lint` passed on 2026-04-07 after setting `ksp.incremental=false` in
  repo-level Gradle properties
- release changelog/play-notes generator verified on 2026-04-07 in an isolated
  worktree for first-release, later-release, dirty-worktree, and tag-collision
  scenarios

Not yet fully re-verified after the whole-app UX parity slice in this session:
- on 2026-04-23,
  `source scripts/android-env.sh && run_gradle :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin :app:assembleDebug -Pksp.incremental=false`
  passed after the dashboard startup warm-start/cache refactor
- on 2026-04-23,
  `source scripts/android-env.sh && run_gradle :core:data:testDebugUnitTest --tests 'com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepositoryTest.refresh ignores device provider failure and skips writes when visited disabled' -Pksp.incremental=false`
  still timed out under `UncompletedCoroutinesError`; the repository unit lane
  needs one more follow-up pass before the startup refactor is fully test-clean
- `./scripts/test.sh` reran on 2026-04-08; the new
  `:core:data:testDebugUnitTest` power-history coverage passed, but the broad
  emulator lane still failed in existing connected Android tests under
  `:feature:timetracking:connectedDebugAndroidTest`
- `run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin -Pksp.incremental=false`
  reran on 2026-04-08 for the travel-alert polish slice; dashboard compile
  succeeded, but the full repository unit-test task still failed in existing
  weather-location tests that are outside the travel-alert scope
- `run_gradle :core:data:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:dashboard:compileDebugAndroidTestKotlin :app:assembleDebug -Pksp.incremental=false`
  now gets through the updated repository test and dashboard compile path, but
  still fails later in existing app assembly work under `:app:compileDebugKotlin`
  with a missing `app/build/tmp/kotlin-classes/debug/com` output path
- `make screenshots` still cannot complete reliably when the local
  `Pixel_5_API_31` emulator exits before `adb` exposes a serial
- `make test-emulator` failed again on 2026-04-15 before connected tests could
  start because the local `Pixel_5_API_31` emulator process exited before it
  appeared in `adb devices`; the recent log again showed the existing
  `child_port_handshake` / `bootcompleted.ini` cleanup issue
- Local Info verification did not include a connected Android test run
  because `adb devices` reported no attached emulator or physical device during
  the narrowed dashboard-card pass
- fresh light-and-dark screenshot exports for the redesigned dashboard,
  settings, visited, time-tracking, and about screens
- end-to-end physical-device smoke pass with the explicit `make test-device`
  path
- signed release AAB generation with real keystore
- Play internal upload with real service account

## Immediate Next Steps

1. Resolve the current intermittent emulator launch crash, then rerun the full
   light-and-dark `make screenshots` parity pass.
2. Review the new dashboard weather/surf and visited screenshots against the macOS
   reference set and tune spacing, card density, and vertical order from the
   rendered output.
3. Review the redesigned settings/about/time-tracking screens the same way and
   tighten any sections that still look form-heavy or generic.
4. Screenshot-review the new power card and tune its chart density, copy, and
   metric order from rendered device output.
5. Extend time tracking export parity after the new interruption-reporting UI
   and metrics settle.

## Parallel-Safe Workstreams

Recommended secondary task while provider work is active:
- keep the emulator-first `make test` loop as default, then use `make test-device`
  only for explicit phone-side smoke checks

Handoff doc:
- [parallel-task-ui-smoke-tests.md](./parallel-task-ui-smoke-tests.md)

## Update Instructions

When work lands:
- move finished items from `Not Started` to `Completed`
- add any active work to `In Progress`
- record new verification results
- keep this file chronological and factual rather than aspirational
