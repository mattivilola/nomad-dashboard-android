# Security Best Practices Report

Date: 2026-04-07

## Executive Summary

The repository is close to being publishable as open source from a source-control perspective: tracked local secret files, keystores, and Play service-account files were not found in the current tree, and the ignore rules are in place. The main blocker is the runtime credential model. User-supplied API keys are currently injected at build time into `BuildConfig` and Android manifest metadata, which bakes them into shipped artifacts and contradicts the documented requirement that user-supplied secrets stay local.

The second blocker is that the existing device-local settings path is incomplete and not secure enough for secrets as currently implemented. A Tankerkönig key field exists in `AppSettings` and Proto DataStore, but the settings UI does not expose it and the provider path does not read it. If that field is wired up later without further changes, it will be stored as plaintext in app data and can also be included in Android backup because `allowBackup` is enabled and no exclusion rules are present.

## Scope

- Reviewed tracked source files, build scripts, config examples, and release docs.
- Searched the current tree for common secret and credential patterns.
- Checked git history for tracked `Config/Signing.env`, `Config/AppConfig.env`, and keystore-style files by path.
- Did not run a dedicated history secret scanner such as `gitleaks` or `trufflehog` because none was installed in the environment.

## Critical Findings

### ND-SEC-001: User-supplied provider keys are compiled into the shipped app

Impact: a user-provided API key becomes extractable from the installed APK/AAB instead of remaining device-local.

Evidence:
- `core:data` injects `NOMAD_TANKERKOENIG_API_KEY` into `BuildConfig` at [core/data/build.gradle.kts](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/build.gradle.kts#L18).
- The runtime provider reads that compiled constant in [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt#L186) and [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt#L190).
- The app build also injects `NOMAD_MAPS_API_KEY`, `NOMAD_PLACES_API_KEY`, `NOMAD_RELIEFWEB_APP_NAME`, and `NOMAD_TELEMETRYDECK_APP_ID` into build outputs in [app/build.gradle.kts](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/build.gradle.kts#L32) and [app/build.gradle.kts](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/build.gradle.kts#L35).
- The Google Maps key is placed into manifest metadata in [AndroidManifest.xml](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/main/AndroidManifest.xml#L32).

Why this matters:
- `BuildConfig` values are trivial to recover from a decompiled app.
- Manifest metadata is directly inspectable from the installed package.
- This conflicts with the documented policy in [docs/android-port-plan.md](/Users/matti/Development/ILOapps/nomad-dashboard-android/docs/android-port-plan.md#L95) and [docs/architecture.md](/Users/matti/Development/ILOapps/nomad-dashboard-android/docs/architecture.md#L224).

Required remediation:
- Stop sourcing user-supplied provider credentials into `BuildConfig` and manifest placeholders.
- Move user-managed provider credentials to an in-app settings flow that writes only on-device after install.
- Keep signing keys and Play credentials local-only in the release tooling; those are acceptable in local env files because they are not part of the shipped app.

## High Findings

### ND-SEC-002: The app has a device-local credential field, but runtime ignores it

Impact: the codebase creates a false impression that device-local credential storage already exists, while the actual runtime still depends on compile-time secrets.

Evidence:
- `AppSettings` already defines `tankerkonigApiKey` in [AppSettings.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/model/src/main/java/com/iloapps/nomaddashboard/core/model/AppSettings.kt#L45).
- The key is persisted in Proto DataStore in [app_settings.proto](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/main/proto/app_settings.proto#L23) and mapped in [SettingsProtoMapper.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/main/java/com/iloapps/nomaddashboard/core/datastore/SettingsProtoMapper.kt#L42) and [SettingsProtoMapper.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/main/java/com/iloapps/nomaddashboard/core/datastore/SettingsProtoMapper.kt#L65).
- The settings screen exposes only toggles and no credential entry path in [SettingsScreen.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/feature/settings/src/main/java/com/iloapps/nomaddashboard/feature/settings/SettingsScreen.kt#L43).
- The fuel provider uses `FuelProviderLocalConfig` sourced from compiled `BuildConfig`, not `AppSettings`, in [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt#L188).

Required remediation:
- Add an explicit settings UI for user-supplied provider credentials.
- Route the provider layer to consume credentials from settings storage instead of build-time env.
- Remove the dead compile-time key path after the migration to avoid ambiguity.

### ND-SEC-003: Secrets moved into settings would still not remain strictly local

Impact: if API keys are later stored in the current settings store without further hardening, they remain plaintext in app storage and can be included in Android backup.

Evidence:
- The settings file is written directly by Proto DataStore with no encryption layer in [AppSettingsSerializer.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/datastore/src/main/java/com/iloapps/nomaddashboard/core/datastore/AppSettingsSerializer.kt#L18).
- The settings file location is the standard app data file `app-settings.pb` in [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt#L176).
- Android backup is enabled in [AndroidManifest.xml](/Users/matti/Development/ILOapps/nomad-dashboard-android/app/src/main/AndroidManifest.xml#L15).
- No backup rules or backup exclusion XML were found in the tracked app resources during this review.

Required remediation:
- Store provider credentials in an encrypted on-device store backed by Android Keystore, not the general Proto settings file.
- Disable backup for secret-bearing stores or add explicit backup exclusions.
- Reassess whether visited history and time-tracking data should also be excluded from cloud backup.

## Medium Findings

### ND-SEC-004: Network logging can expose user API keys through query parameters

Impact: a user-provided Tankerkönig key can be written to Logcat and any downstream log collectors because the request URL includes `apikey=...`.

Evidence:
- The shared `OkHttpClient` always installs `HttpLoggingInterceptor(Level.BASIC)` in [NomadDataModule.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/data/src/main/java/com/iloapps/nomaddashboard/core/data/di/NomadDataModule.kt#L67).
- Tankerkönig sends the credential as a query parameter in [TankerkoenigService.kt](/Users/matti/Development/ILOapps/nomad-dashboard-android/core/network/src/main/java/com/iloapps/nomaddashboard/core/network/api/TankerkoenigService.kt#L8).

Required remediation:
- Remove HTTP logging from release builds.
- Prefer a redacting interceptor or no URL logging at all in debug builds when credentials are present in query parameters.

## Positive Observations

- Secret-bearing local files are ignored in [/.gitignore](/Users/matti/Development/ILOapps/nomad-dashboard-android/.gitignore#L1).
- Only example env files are tracked in `Config/`.
- A path-based git history check did not find committed `Config/Signing.env`, `Config/AppConfig.env`, or keystore files.
- Release scripts load signing and Play credentials from local env files and do not print the secret values themselves.

## Recommended Publication Gate

Before publishing the repository as open source:

1. Remove all user-supplied API keys from build-time injection paths.
2. Implement an in-app credential entry flow backed by encrypted local storage.
3. Disable or exclude backup for secret-bearing local stores.
4. Remove or redact HTTP logging around authenticated requests.
5. Run a dedicated full-history secret scan with `gitleaks` or `trufflehog` before making the repository public.

## Suggested Next Implementation Slice

- Add a secure credentials store module using Android Keystore-backed encryption.
- Expose credential fields in the Settings screen only for providers that need user-supplied keys.
- Update the fuel provider to read the Tankerkönig key from that secure local store.
- Remove `NOMAD_TANKERKOENIG_API_KEY`, `NOMAD_MAPS_API_KEY`, and `NOMAD_PLACES_API_KEY` from shipped build config and manifest injection.
- Update the docs to describe the new local-only credential flow and the open-source publication policy.
