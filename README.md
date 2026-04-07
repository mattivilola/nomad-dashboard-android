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
make lint
```

Android Studio should use its bundled JDK automatically through the helper scripts. Local signing and Play publishing values belong in gitignored `Config/Signing.env` and `Config/AppConfig.env`.

`make connect-wireless` will prompt for the pairing endpoint and the connect endpoint shown under Android's Wireless debugging screen.
The flow is: pair endpoint, pairing code, then connect endpoint.

## Documentation

- [Docs index](./docs/README.md)
- [Android port plan](./docs/android-port-plan.md)
- [Architecture](./docs/architecture.md)
- [Features](./docs/features.md)
- [Status](./docs/status.md)
- [Testing](./docs/testing.md)
- [Release](./docs/release.md)
