# Nomad Dashboard Android

Native Android port scaffold for Nomad Dashboard.

## Current Scope

- Kotlin + Jetpack Compose single-activity app
- Multi-module Android Studio project
- Hilt, Room, Proto DataStore, Retrofit/OkHttp, WorkManager-ready setup
- Adaptive navigation for dashboard, settings, visited map, time tracking, and about
- Real bootstrap data flow for connectivity, public IP geolocation, power, and Open-Meteo weather
- Optional local price level card using Eurostat in Europe and HUD USER plus US Census Geocoder in the US
- Local-first build, release, signing, and Google Play internal-track helper scripts

## Quick Start

```sh
make bootstrap
make connect-wireless
make build
make screenshot
make test
make screenshots
make lint
```

Android Studio should use its bundled JDK automatically through the helper scripts. Local signing and Play publishing values belong in gitignored `Config/Signing.env`. `Config/AppConfig.env` is only for non-secret helper config and is not used for provider credentials or Maps/Places SDK keys. The visited Google Maps view and the dashboard emergency-care hospital lookup both use an app-level Android Maps/Places key from local `local.properties`, Gradle properties, or environment variables, not from the in-app Settings screen; enable Places API (New) on that same restricted key before using emergency care. User-supplied provider credentials such as the HUD USER token, Germany Tankerkonig key, and the ReliefWeb app name must be entered in the app's Settings screen after install and are stored only in encrypted device-local storage. Europe price levels work without the HUD token; the token is only used for the US 1-bedroom rent benchmark.

`make screenshots` runs the deterministic emulator review lane and exports local PNGs to `output/screenshots/android/phone`. For faster iteration, use `SCREEN=dashboard make screenshots` or replace `dashboard` with `settings`, `visited`, `timetracking`, or `about`.

`make screenshot` captures the current screen from the selected adb target, prefers a connected physical device over an emulator, and stores a timestamped PNG in `output/screenshots/device`.
`make screenshot-device` does the same but fails unless a physical Android device is connected.

`make connect-wireless` will prompt for the pairing endpoint and the connect endpoint shown under Android's Wireless debugging screen.
The flow is: pair endpoint, pairing code, then connect endpoint.

## Documentation

- [Changelog](./CHANGELOG.md)
- [Docs index](./docs/README.md)
- [Agent guidance](./AGENTS.md)
- [Claude guidance](./CLAUDE.md)
- [Android port plan](./docs/android-port-plan.md)
- [Architecture](./docs/architecture.md)
- [Features](./docs/features.md)
- [Status](./docs/status.md)
- [Testing](./docs/testing.md)
- [Release](./docs/release.md)
