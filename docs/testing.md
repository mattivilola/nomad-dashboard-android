# Testing

Last updated: 2026-04-07

## Local Setup

From the repo root:

```sh
cd /Users/matti/Development/ILOapps/nomad-dashboard-android
make bootstrap
make doctor
```

The helper environment does the following:
- resolves Android Studio bundled JDK instead of shell Java 11
- uses repo-local Gradle state where possible
- exposes Android SDK platform tools in PATH

## Build And Static Checks

Build debug APK:

```sh
make build
```

Install and launch the debug app:

```sh
make run
```

Current behavior:
- prefers the first attached non-emulator Android device when both a phone and
  an emulator are connected
- falls back to the first booted emulator when no physical device is attached
- honors an explicit `ANDROID_SERIAL=<serial> make run` override
- builds the debug APK before install and launch

Run unit tests:

```sh
make test
```

Current behavior:
- uses the default emulator workflow
- boots `Pixel_5_API_31` by default, or `NOMAD_ANDROID_AVD=<name> make test`
- starts the emulator headless by default; set `NOMAD_ANDROID_EMULATOR_WINDOW=1`
  if you want the emulator window
- waits for the emulator to finish booting
- sets `ANDROID_SERIAL` so Gradle targets the emulator instead of any attached phone

Run unit tests plus connected tests on the emulator only:

```sh
make test-emulator
```

Current behavior:
- boots `Pixel_5_API_31` by default, or `NOMAD_ANDROID_AVD=<name> make test-emulator`
- starts the emulator headless by default; set `NOMAD_ANDROID_EMULATOR_WINDOW=1`
  if you want the emulator window
- waits for the emulator to finish booting
- sets `ANDROID_SERIAL` so Gradle targets the emulator instead of any attached phone

Run unit tests plus connected tests on a physical device:

```sh
make test-device
```

Current behavior:
- selects the first attached non-emulator Android target
- intended for occasional smoke verification, not the default unattended loop

Run lint:

```sh
make lint
```

Current verified result:
- `make build` passed on 2026-04-07 after the credential-hardening slice
- `make lint` passed on 2026-04-07 after the credential-hardening slice
- debug APK was installed and launched on a physical Android phone over wireless debugging

Latest verification attempt:
- on 2026-04-07, `make build` passed after removing build-time provider
  credential injection and adding encrypted device-local storage plus settings UI
- on 2026-04-07, `make lint` passed after the credential-hardening slice
- on 2026-04-07, `make test` failed in `:app:connectedDebugAndroidTest`
  because the instrumentation runtime could not resolve
  `androidx.test.internal.platform.app.ActivityInvoker` while the existing app
  smoke tests were trying to launch `MainActivity`
- the default workflow now routes `make test` through the emulator path and
  reserves the phone for explicit smoke checks via `make test-device`
- Hilt-backed Android library modules needed both
  `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and
  `androidTestImplementation(libs.androidx.test.runner)` so empty
  instrumentation APKs do not crash before reporting `0 tests`
- the app smoke tests now use stable shell navigation tags instead of depending
  on every bottom-bar label remaining visible at emulator width
- the settings smoke flow now also covers the masked Tankerkonig API key field
  persisting across activity recreation

## APK Location

Current debug APK output:

- [app-debug.apk](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/build/outputs/apk/debug/app-debug.apk)

## Install On A Phone

### USB

1. Enable Developer Options.
2. Enable USB debugging.
3. Connect the phone by USB.
4. Accept the trust/debug prompt on the phone.
5. Verify:

```sh
adb devices -l
```

6. Install and launch:

```sh
make run
```

If both a phone and an emulator are attached, `make run` targets the phone by
default.

Or install manually:

```sh
adb install -r /Users/matti/Development/ILOapps/nomad-dashboard-android/app/build/outputs/apk/debug/app-debug.apk
```

### Wi-Fi / Wireless Debugging

1. On the phone, enable `Wireless debugging` in Developer Options.
2. Choose `Pair device with pairing code`.
3. Run:

```sh
make connect-wireless
```

The helper will prompt for:
- `PHONE_IP:PAIR_PORT`
- pairing code
- `PHONE_IP:CONNECT_PORT`

Then install and launch:

```sh
make run
```

If both a wireless-debugged phone and an emulator are attached, `make run`
targets the phone by default. Use `ANDROID_SERIAL=<emulator-serial> make run`
when you explicitly want the emulator.

Current repo status:
- verified working on a physical device after wireless ADB reconnect

## Troubleshooting Install Failures

### `INSTALL_FAILED_USER_RESTRICTED`

This means the phone blocked the installation. The Android app built correctly,
but the device refused the ADB install.

This is especially common on Xiaomi / Redmi / HyperOS / MIUI devices.

Try these steps on the phone:

1. Open `Settings > Developer options`.
2. Enable `USB debugging`.
3. Enable `Install via USB`.
4. If present, enable `USB debugging (Security settings)`.
5. Keep the phone unlocked and watch for an install/security confirmation.
6. If it still fails, temporarily disable `MIUI optimization` or `HyperOS optimization`, then retry.

Retry:

```sh
make run
```

Notes:
- The app already built successfully if the error appears during install.
- On some Xiaomi devices, the install confirmation appears as a system prompt or
  notification rather than a full-screen dialog.
- Wireless debugging still requires the device to allow ADB-driven installs.
- If repeated install confirmations remain on Xiaomi / Poco / HyperOS devices,
  prefer Android Studio device mirroring so the prompt can at least be handled
  from the desktop, or switch routine instrumentation loops to an emulator.

## Lessons Learned

- Physical-device `make test` is only hands-off on devices that allow silent ADB
  reinstall of both the app APK and the instrumentation APK. Xiaomi / Poco /
  HyperOS devices may keep prompting for install approval even when debugging is
  already trusted.
- Android Studio device mirroring helps with manual approval loops, but it does
  not remove OEM install restrictions. For unattended local iteration, an
  emulator is the safer default.
- Hilt-based Android library modules must package the AndroidX test runner in
  `androidTestImplementation`, even when they have zero Android tests, because
  `connectedDebugAndroidTest` still launches an instrumentation APK for those
  modules.
- UI smoke assertions should target stable shell labels and durable route
  content. Placeholder-specific assertions become stale as features move from
  scaffold to implementation.
- Provider credentials must be verified through the in-app Settings flow, not
  through local env-file injection into the app build.
- If a physical device is connected, `make test` becomes slower and more fragile
  on OEM-skinned phones. The default workflow now routes `make test` to the AVD
  so the phone is not part of the normal loop.

## Run From Android Studio

1. Open `/Users/matti/Development/ILOapps/nomad-dashboard-android` in Android Studio.
2. Let Gradle sync.
3. Select your emulator or phone.
4. Press Run.

## Emulator Workflow

Preferred default for unattended connected tests:

```sh
make start-emulator
make test
```

Notes:
- the current machine already has `Pixel_5_API_31` configured
- `make test` and `make test-emulator` avoid the Poco / HyperOS
  install-approval loop by targeting the emulator explicitly
- the emulator boots headless by default so connected tests can run unattended
- keep the physical phone disconnected from routine test loops unless you are
  doing manual smoke verification
- use `make test-device` only when you intentionally want connected tests on the
  phone

## Manual Smoke Checklist

Perform these checks on the first installed build:

### App shell

- app launches without crash
- bottom navigation appears on phone
- navigation rail appears on larger window sizes
- all five top-level destinations open

### Dashboard

- refresh button works
- connectivity information renders
- battery information renders
- public IP or travel context is shown when network is available
- weather section shows data when IP geolocation returns coordinates
- when fuel prices are enabled, the fuel card shows the cheapest nearby diesel
  and gasoline rows for Spain, France, Italy, or Germany
- in Germany without a Tankerkonig key saved in the app's Settings screen, the
  fuel card shows a configuration-required message instead of using any shared
  bundled key
- without device location and without usable IP location/country, the fuel card
  shows an unavailable state instead of crashing

### Settings

- toggles change state
- settings persist after app relaunch
- the masked Tankerkonig key field persists after save and app relaunch
- card reordering works
- card width toggling persists
- visited device-location toggle only prompts from the visited screen, not from Settings

### Visited history

- visited screen shows disabled state when visited places are off
- visited screen shows empty state before any capture is stored
- visited screen shows location-permission CTA when visited device capture is on but permission is missing
- dashboard refresh records an IP-based place and country day when external IP location resolves
- granting location permission and refreshing records a device-based visit
- country-day summaries show yearly totals, monthly totals, and inferred gap days

### Time tracking

- time-tracking screen shows disabled guidance when project tracking is off
- adding a local project makes it immediately selectable for tracking
- starting tracking requires a selected project
- an active session shows elapsed time in-app and posts a persistent notification
- stopping tracking closes the active session and moves it into recent entries

### Build and environment

- `make build` still succeeds after app launch
- `make test` still succeeds
- `make lint` still succeeds

## Current Automated Coverage

Implemented now:
- settings proto round-trip unit test
- visited model summary tests
- visited history store merge and country-day logic tests
- repository refresh tests for IP capture, device capture, and disabled-state behavior
- fuel provider tests for Spain, France, Italy, and Germany config handling
- repository fuel refresh tests for device-first lookup, IP fallback, and unavailable-state wiring
- time-tracking repository tests for project creation, start/stop behavior, single-active-session enforcement, and persisted active-session recovery
- dashboard repository tests for disabled, idle, and active time-tracking summaries
- foreground-service runtime tests for notification formatting and stop-command handling
- Compose UI smoke tests for:
  - app launch into dashboard
  - top-level navigation across all five shell destinations
  - time-tracking disabled guidance on the tracking route
  - stable dashboard shell rendering without live-network assertions
  - persisted `Expand weather forecast` toggle across activity recreation

File:
- [SettingsProtoMapperTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/test/java/com/iloapps/nomaddashboard/core/datastore/SettingsProtoMapperTest.kt)
- [VisitedModelsTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/model/src/test/java/com/iloapps/nomaddashboard/core/model/VisitedModelsTest.kt)
- [RoomVisitedHistoryStoreTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/visited/RoomVisitedHistoryStoreTest.kt)
- [DefaultFuelPriceProviderTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/fuel/DefaultFuelPriceProviderTest.kt)
- [RoomTimeTrackingRepositoryTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/timetracking/RoomTimeTrackingRepositoryTest.kt)
- [DefaultNomadDashboardRepositoryTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/repository/DefaultNomadDashboardRepositoryTest.kt)
- [TimeTrackingForegroundServiceRuntimeTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/test/java/com/iloapps/nomaddashboard/feature/timetracking/runtime/TimeTrackingForegroundServiceRuntimeTest.kt)
- [MainActivitySmokeTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/androidTest/java/com/iloapps/nomaddashboard/MainActivitySmokeTest.kt)
- [SettingsSmokeTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/androidTest/java/com/iloapps/nomaddashboard/SettingsSmokeTest.kt)

Planned next:
- storage migration tests
- physical-device notification smoke verification for the time-tracking foreground service
