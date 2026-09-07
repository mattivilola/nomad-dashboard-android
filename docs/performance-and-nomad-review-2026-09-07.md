# Android responsiveness and nomad feature review

Date: 2026-09-07. Reviewed commit: `7932a40c4abe9833cea9e483bea52715ef316dda`.

This records the original review; the implementation follow-through is summarized below. Existing user edits to AGENTS.md were preserved. Evidence comes from source inspection and current Android documentation. No physical device was attached to ADB, so frame times, startup latency, memory, traffic and battery improvements are unmeasured. No build or application suite was run for this documentation-only review.

## Recommendation

First make cached content and local actions independent of external refreshes. Then reduce unnecessary network, timer and map work. Add useful local-first features after those foundations. Room is an on-device database here; the slow-network risk comes from external providers, not a remote Room database.

Preserve the existing cached section restoration, Room history, single-flight refresh protection, lifecycle-aware route collectors, retained navigation state and background country-shape parsing. These are useful foundations, but asynchronous requests alone do not guarantee prompt publication or main-thread safety.

## Performance findings

Paths below are repository-relative; line references refer to the reviewed source.

### Highest priority: bulk fuel work runs on the UI thread

`feature/dashboard/.../DashboardViewModel.kt:93-105` starts refresh in viewModelScope. The repository's supervisorScope/plain async calls inherit Main; neither the fuel provider nor its caller adds an IO boundary. `core/data/.../fuel/DefaultFuelPriceProvider.kt:170-211` reads both Italian ResponseBody objects into strings, parses both complete CSVs and merges stations/prices synchronously. Its CSV parser (`:510-566`) materializes lines and cells. Retrofit being suspending does not offload this post-response work. This is a concrete main-thread blocking path; its duration depends on the feed and device and has not been profiled here.

Inject dispatchers: consume raw response bodies on IO and perform substantial parsing/indexing on a bounded worker dispatcher. Make the provider main-safe at its API boundary, preserve cancellation, and avoid repeated full-dataset allocations through catalog caching. Verify with representative local CSV fixtures, a dispatcher assertion and a physical-device main-thread trace.

### 1. High: slow providers hold back independent results

`core/data/.../repository/DefaultNomadDashboardRepository.kt:230-300` reads connectivity and power early, but waits for location-dependent sections, cache writes, local history and marine before publishing the new dashboard snapshot. `refreshLocationDependentSections` at lines 583-646 launches providers concurrently, then returns only after all results finish. This is a publication barrier despite network concurrency. A slow advisory or fuel result can delay otherwise-ready cards.

Publish cheap local readings immediately; publish each section separately as it completes. Preserve existing content during refresh, with section-specific source, location, last-success time and error state. Give each provider a total deadline and independent retry. Use a refresh generation/location key so late results cannot overwrite newer context. Persist successful sections independently without putting disk writes ahead of their visible result.

### 2. High: location bootstrap waits too long and can duplicate requests

`DefaultNomadDashboardRepository.kt:431-488` checks whether device location has already finished after a single yield; otherwise it awaits IP before reconsidering device location. `:1402-1423` chains primary IP lookup, plain-IP fallback and by-address geolocation. Each uses up to three attempts (`TransientRetry.kt:9-30`); the shared client permits a 15-second call (`di/NomadDataModule.kt:100-118`). Slow fallback scenarios can accumulate over a minute. This is a code-derived risk, not a measured stall duration.

Use an actual bounded device-location grace period and overall IP budget; usable device coordinates must not wait for public IP. Let public IP update its own field later. The existing device-promotion pass (`:348-401`) should re-fetch only affected sections when location materially changes, rather than repeating the complete provider set. Cache context must visibly retain its original location while the user moves.

### 3. High: fuel refresh does disproportionate work

`core/data/.../fuel/DefaultFuelPriceProvider.kt:64-108` maps Spain's country feed; `:111-160` paginates French results; `:170-175` downloads and parses two Italian catalog CSVs; `:245-295` loops through German search tiles sequentially. Restoring a dashboard section on startup does not prevent these requests on refresh.

Add source-appropriate TTLs and request coalescing. Reuse country catalogs independently of the current location, then filter locally; cache nearby results by country/location region with a displacement threshold. Serve prior content while refreshing, expose its age, and allow explicit per-card refresh. In low-data mode, defer large catalog downloads unless explicitly requested. Use bounded concurrency only where provider limits permit it.

### 4. High: local timer actions trigger unrelated network refresh

`feature/timetracking/.../TimeTrackingViewModel.kt:166,182,207,223` calls full dashboard refresh after start, stop, allocation and interruption. These actions already have local repository state flows.

Update the local timer/report state directly and let those flows update the dashboard. Remove external refresh from these local commands. Verify offline start, pause, allocation and interruption issue zero provider requests and acknowledge locally committed state promptly.

### 5. High for growing histories: list and map rendering costs

`feature/visited/.../VisitedScreen.kt:319-332` renders all saved places inside one lazy-list item using a regular Column; `:808-848` similarly expands travel stops, and `:964-992` expands daily rows. The outer LazyColumn cannot virtualize individual records. Use keyed lazy rows, bounded history previews or pagination; retain year/month summaries separately.

The map at `:699-724` submits all country polygons, including unvisited countries, and rebuilds coordinate lists and MarkerState objects. Country-shape loading already runs off-main and caches correctly. Reduce overlays to relevant countries, use simplified boundaries appropriate to zoom, memoize converted geometry, retain marker identity, and cluster/cap large marker sets. Offer the travel list immediately while the map initializes. Measure before considering a different map SDK.

### 6. Medium: timer work continues when it is unnecessary

`feature/timetracking/.../TimeTrackingScreen.kt:150-154,667-679` ticks at screen scope even while paused. Dashboard's timer at `DashboardScreen.kt:1830,2006-2018` is similarly always enabled while composed. Enable ticking only for live elapsed-time or time-dependent feedback, isolate state reads to small composables, and suspend UI tickers when not visible.

`app/.../TimeTrackingForegroundService.kt:120-136` rebuilds and posts its notification every second. Use Android's notification chronometer and update app-generated notification content only on meaningful state changes. Preserve session timestamps, recovery and notification actions; elapsed accounting must not depend on the number of UI ticks.

### 7. Medium: cancellation and offline work need tighter boundaries

`DefaultNomadDashboardRepository.kt:176-188,1405-1423` uses broad runCatching around suspending work. Cancellation can be converted into fallback/error handling. Explicitly rethrow CancellationException throughout provider orchestration; complete/cancel coalesced waiters and clean up in a cancellation-safe finally path. Test cancellation followed immediately by another refresh so no stale single-flight signal can strand callers. The retry helper itself normally rethrows non-retryable cancellation; broad callers are the concern.

`core/data/.../monitor/SystemTelemetryReader.kt:68-70` always runs both TCP probes, even if the first succeeds. Each socket has a 1.5-second connect timeout (`:174-184`). Publish passive connectivity immediately; skip active probing without a validated connection and invoke the fallback only after failure. Metered/roaming/Data Saver/Battery Saver policies should reduce optional work without hiding essential cached content.

Metric retention is already bounded. Individual insert/prune/read operations in `DefaultNomadDashboardRepository.kt:1017-1081` are a lower-priority batching opportunity; avoid replacing the existing database architecture.

### 8. Medium: release optimization and performance measurement are missing

`app/build.gradle.kts:94-96` disables both minification and resource shrinking. No app-specific baseline-profile or macrobenchmark setup was found in build files. Enable R8/resource shrinking in a separately verified release configuration, validate serialization, Room, Hilt, Maps and provider flows, then add an app Baseline Profile for startup/navigation/scrolling. Measure actual APK/AAB installed size and startup rather than promise a percentage gain. Audit production dependency scope, including the test runner currently declared as an implementation dependency.

## Shared Android/macOS product direction

The live task **Optimize Nomad app performance** was read and Android findings were sent to it as requested. Its completed [local review](../../nomad-dashboard-macosx/docs/performance-and-nomad-review-2026-09-07.md) was also read and compared. It independently found delayed whole-dashboard publication, repeated metric-history file work, per-second historical time-tracking work and refreshes that bypass caches. Android already persists several last-successful cards that macOS currently keeps only in memory: Android's startup cache behavior is worth carrying to macOS. Both platforms should share the behavior below; implementation should remain native. These were the aligned review proposals. The user subsequently authorized implementation; the native scope delivered is recorded below.

| Priority | Feature | Useful first version | Alignment and weight control |
| --- | --- | --- | --- |
| First | Travel and time-report export | Android Sharesheet export for selected year/month and time reports; CSV plus human-readable summary | macOS already documents clipboard travel summaries and time exports. Agree columns, timezones, observed/inferred provenance and schema version; export excludes credentials. |
| First | Low-data / low-impact mode | Automatic policy plus a clear override; last-good cards, per-card refresh and download age | Same intent on both platforms; Android respects metering, roaming, lifecycle and battery; Mac additionally respects CPU/thermal pressure. |
| Next | Compact work-readiness view | Existing connection, VPN, battery and local-context signals with optional detail expansion | Adopt the macOS proposal on Android as a configurable compact view; show uncertainty rather than inventing a precise readiness score. |
| Next | Offline travel essentials | Pin lodging address, personal emergency contacts, saved destinations and existing travel context | User-selected local text first; show source/location/age. Cached hospital data cannot imply current availability. Full offline map packs need a separate provider/storage review. |
| Next | Workplace connection history | Save a place with observed connection quality, reliability notes, power availability and visit date | Reuse existing telemetry; label passive throughput as observed usage rather than a bandwidth test. Active tests are explicit and data-budgeted. No persistent SSID/location collection without opt-in. |
| Next | Home/client timezone work planner | Saved zones, working-hour overlap and next suitable meeting window | Offline timezone calculations; Android quick access/widget, Mac compact menu-bar presentation. Avoid needing calendar account integration for the first version. |
| Later | Correctable travel ledger and stay planning | Edit missed/incorrect visits, confirm inferred days, export a travel record, add user-defined stay reminders | Preserve observed/inferred/manual provenance and auditable edits. Any jurisdiction-specific visa/tax calculator requires separately validated rules and user circumstances; do not label current inferred days as legal eligibility. |
| Later | Portable backup/import | Explicit versioned file export/import with preview, duplicate detection and conflict handling | Establish shared IDs/schema before considering optional sync. Do not silently sync credentials or location history. |
| Later | Quiet contextual notifications | Opt-in sustained connection/VPN/power changes with deduplication and quiet hours | Reuse existing events; Android delivery must respect background limits. Avoid keeping a foreground service alive solely for speculative alerts. |

Keep extra features optional and off the primary dashboard until enabled. Prioritize useful actions over additional always-refreshing cards. Match semantics, sources and data formats across platforms rather than forcing identical layouts or platform telemetry capabilities.

## Implementation order and acceptance evidence

1. Fix main-thread work and independent section publication, location budgets, cancellation and local-only timer actions. Fake stalled providers must leave navigation, scrolling and local actions responsive; a fast card must publish before a held slow card; cancelled/stale results must not overwrite current context.
2. Add provider cache/revalidation policy and low-data behavior. Count fake requests/bytes across cold start, repeated refresh, rapid taps, device promotion, airplane mode and recovery. Repeated refresh inside TTL should not repeat expensive catalogs.
3. Gate timer work and virtualize/simplify history/maps. Seed small and multi-year histories; compare recomposition, main-thread slices, frame overruns and memory on a lower-end phone. Paused screens should not update every second solely for their timer.
4. Verify an optimized release and add startup/navigation/scroll Baseline Profiles plus Macrobenchmarks. Record cold/warm launch, first cached-content time, p50/p95 action latency, frame overruns, memory, request counts/bytes and idle battery behavior. Test slow responses independently from throughput limits and total loss of connectivity.
5. Implement export parity, then offline essentials and work-planning conveniences.

Provisional experience targets, to calibrate on a named physical device: visible local-action acknowledgement within 100 ms; cached useful content within 1 second of launch; no network-dependent spinner that disables navigation; zero external calls for timer-only actions. These are proposed budgets, not measured current results. Use an optimized/profileable build and real hardware for performance claims; emulator functional tests can verify ordering and failure behavior.

## Platform references

- [Android offline-first data architecture](https://developer.android.com/topic/architecture/data-layer/offline-first) supports local data as the source presented to the UI while network work updates it.
- [Compose performance phases](https://developer.android.com/develop/ui/compose/performance/phases) explains keeping frequently changing state reads narrowly scoped.
- [App Baseline Profiles](https://developer.android.com/develop/ui/compose/performance/baseline-profiles) cover application paths beyond the profile shipped with Compose.
- [R8 release optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization) documents code and resource optimization.
- [Notification chronometer API](https://developer.android.com/reference/android/app/Notification.Builder) provides elapsed-time display without app-side per-second notification rebuilding.

## Implementation follow-through

The authorized implementation now includes off-main fuel parsing with bounded coalesced catalog caches; independently published dashboard cards with persisted success/location metadata, TTL reuse and explicit refresh; bounded device/IP bootstrap; cancellation-safe refresh cleanup; passive foreground telemetry and reduced work in automatic/Low Data mode. Local timer commands do not trigger remote refresh. Timer labels tick only while needed, and the foreground notification uses the system chronometer. Travel rows are lazy and keyed, with only relevant country map overlays retained.

Companion adds editable offline essentials, explicitly saved workplace observations, a home/client IANA timezone overlap planner, compact work readiness, opt-in sustained foreground alerts, and versioned nonsecret backup/merge. It does not infer cafe identity or stationary visits from saved coordinates. Travel tools add manual country-day corrections with original provenance, user-defined stay countdowns, year CSV/summary export, and transactional backup/import. Time reports export CSV/summary and completed-session backups. Country CSV v1 matches macOS columns; platform-native JSON backups are explicitly separate.

The release configuration enables R8 and resource shrinking and includes Baseline Profile generation and cold-start/scroll Macrobenchmark sources. Current verification and release evidence is maintained in `docs/status.md`; physical-device latency, frame, traffic and battery improvements are not claimed from emulator functional checks. Widgets, offline map packs, calendar integration, legal eligibility calculators and cross-platform JSON/sync remain outside this first native scope.
