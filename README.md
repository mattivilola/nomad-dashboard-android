# Nomad Dashboard Android

Native Android port scaffold for Nomad Dashboard.

## Current Scope

- Kotlin + Jetpack Compose single-activity app
- Multi-module Android Studio project
- Hilt, Room, Proto DataStore, Retrofit/OkHttp, WorkManager-ready setup
- Adaptive navigation for dashboard, settings, visited map, time tracking, and about
- Real bootstrap data flow for connectivity, public IP geolocation, power, and Open-Meteo weather
- Local-first build, release, signing, and Google Play internal-track helper scripts

## Quick Start

```sh
make bootstrap
make connect-wireless
make build
make test
make screenshots
make lint
```

Android Studio should use its bundled JDK automatically through the helper scripts. Local signing and Play publishing values belong in gitignored `Config/Signing.env`. `Config/AppConfig.env` remains local-only and is limited to non-secret app config such as the ReliefWeb app name and package-restricted client keys such as Google Maps. User-supplied provider credentials such as the Germany Tankerkonig key must be entered in the app's Settings screen after install and are stored only in encrypted device-local storage.

`make screenshots` runs the deterministic emulator review lane and exports local PNGs to `output/screenshots/android/phone`.

`make connect-wireless` will prompt for the pairing endpoint and the connect endpoint shown under Android's Wireless debugging screen.
The flow is: pair endpoint, pairing code, then connect endpoint.

## Documentation

- [Docs index](./docs/README.md)
- [Agent guidance](./AGENTS.md)
- [Claude guidance](./CLAUDE.md)
- [Android port plan](./docs/android-port-plan.md)
- [Architecture](./docs/architecture.md)
- [Features](./docs/features.md)
- [Status](./docs/status.md)
- [Testing](./docs/testing.md)
- [Release](./docs/release.md)
