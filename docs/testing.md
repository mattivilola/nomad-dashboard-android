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
- all three commands pass in this repo
- debug APK was installed and launched on a physical Android phone over wireless debugging

Latest verification attempt:
- the visited unit-test coverage now compiles and passes during `testDebugUnitTest`
- the default workflow is being switched so `make test` uses the emulator path
  and the phone is reserved for explicit smoke checks via `make test-device`
- Hilt-backed Android library modules needed both
  `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` and
  `androidTestImplementation(libs.androidx.test.runner)` so empty
  instrumentation APKs do not crash before reporting `0 tests`
- the app smoke test needed to be updated after replacing the visited
  placeholder screen with the implemented visited-history UI

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

### Settings

- toggles change state
- settings persist after app relaunch
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
- Compose UI smoke tests for:
  - app launch into dashboard
  - top-level navigation across all five shell destinations
  - stable dashboard shell rendering without live-network assertions
  - persisted `Expand weather forecast` toggle across activity recreation

File:
- [SettingsProtoMapperTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/test/java/com/iloapps/nomaddashboard/core/datastore/SettingsProtoMapperTest.kt)
- [VisitedModelsTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/model/src/test/java/com/iloapps/nomaddashboard/core/model/VisitedModelsTest.kt)
- [RoomVisitedHistoryStoreTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/visited/RoomVisitedHistoryStoreTest.kt)
- [DefaultNomadDashboardRepositoryTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/test/java/com/iloapps/nomaddashboard/core/data/repository/DefaultNomadDashboardRepositoryTest.kt)
- [MainActivitySmokeTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/androidTest/java/com/iloapps/nomaddashboard/MainActivitySmokeTest.kt)
- [SettingsSmokeTest.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/androidTest/java/com/iloapps/nomaddashboard/SettingsSmokeTest.kt)

Planned next:
- storage migration tests
- time-tracking ledger tests
