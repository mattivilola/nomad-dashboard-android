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
make build
make test
make lint
```

Android Studio should use its bundled JDK automatically through the helper scripts. Local signing and Play publishing values belong in gitignored `Config/Signing.env` and `Config/AppConfig.env`.
