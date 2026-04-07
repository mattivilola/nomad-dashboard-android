# Release Workflow

Last updated: 2026-04-07

## Release Strategy

The Android port uses a local-first release process similar to the macOS repo:

- tracked version metadata in `Config/version.properties`
- tracked release history in `CHANGELOG.md`
- tracked Google Play release notes in `app/src/main/play/release-notes/en-US/default.txt`
- gitignored local signing and Play credentials
- helper scripts in `scripts/`
- Internal testing as the first Google Play target

## Required Local Files

Create these local files from examples:

- `Config/Signing.env`
- `Config/AppConfig.env` for local helper scripts and non-secret helper config;
  it is not used for provider credentials or Google Maps SDK keys
- `local.properties` for local Android SDK paths and optional Maps SDK key
  properties

## Required Values

From `Config/Signing.env`:
- `NOMAD_APPLICATION_ID`
- `NOMAD_DEBUG_APPLICATION_ID_SUFFIX`
- `NOMAD_KEYSTORE_PATH`
- `NOMAD_KEYSTORE_PASSWORD`
- `NOMAD_KEY_ALIAS`
- `NOMAD_KEY_PASSWORD`
- `NOMAD_PLAY_SERVICE_ACCOUNT_JSON`
- `NOMAD_PLAY_TRACK`

From `Config/AppConfig.env` as needed:
- no provider credentials; keep this file for non-secret helper config only

From `local.properties`, Gradle properties, or environment variables as needed:
- debug map key: `nomad.mapsApiKey.debug` or `NOMAD_MAPS_API_KEY_DEBUG`
- release map key: `nomad.mapsApiKey.release` or `NOMAD_MAPS_API_KEY_RELEASE`
- fallback shared map key: `nomad.mapsApiKey` or `NOMAD_MAPS_API_KEY`

Recommended local `local.properties` example:

```properties
nomad.mapsApiKey.debug=YOUR_DEBUG_ANDROID_MAPS_KEY
nomad.mapsApiKey.release=YOUR_RELEASE_ANDROID_MAPS_KEY
```

The build injects the current build type's key into
`com.google.android.geo.API_KEY` in the Android manifest.
That same restricted key powers both the visited map and emergency-care
hospital lookup, so Places API (New) must also be enabled on the chosen key.

Debug SHA-1 note:
- repo helper scripts export `ANDROID_USER_HOME` as
  `/Users/matti/Development/ILOapps/nomad-dashboard-android/.android-home`
- debug builds created through `make build`, `make run`, and other helper
  scripts therefore use the repo-local debug keystore, not
  `~/.android/debug.keystore`
- use this command when registering the debug package
  `com.iloapps.nomaddashboard.dev` in Google Cloud:

```sh
keytool -list -v \
  -alias androiddebugkey \
  -keystore /Users/matti/Development/ILOapps/nomad-dashboard-android/.android-home/debug.keystore \
  -storepass android \
  -keypass android
```

## Commands

Print environment summary:

```sh
make doctor
```

Dry-run release metadata:

```sh
make release-dry-run
```

Build signed bundle:

```sh
make bundle-release
```

Build signed APK:

```sh
make apk-release
```

Upload to internal testing:

```sh
make publish-internal
```

Run end-to-end release flow:

```sh
make release
```

Prepare semantic version bump:

```sh
make release-patch
make release-minor
make release-major
```

Current behavior:
- requires a clean git worktree before it mutates anything
- bumps `MARKETING_VERSION` and `VERSION_CODE`
- prepends a new release entry to `CHANGELOG.md`
- writes concise Google Play "What's new" text to
  `app/src/main/play/release-notes/en-US/default.txt`
- creates a local `Release vX.Y.Z` commit and a local `vX.Y.Z` tag
- does not push; push the release commit and tag manually when ready

If `v0.1.0` or another historic release was already shipped outside this repo,
backfill the corresponding git tag before relying on future changelog diffs.
The bump command now fails fast instead of generating a duplicated changelog
when the current changelog baseline exists but the matching `v*` tag does not.

## Current Status

Implemented:
- version source of truth
- release helper scripts
- publish helper wiring
- local-first changelog and Play release-notes generation during version bumps
- shipped app provider credentials removed from build-time env injection

Not yet verified with real credentials:
- signed release artifact generation
- internal-track upload
- version bump flow with a populated remote repository and manual push follow-up

## Security Rules

- never commit keystores
- never commit Play service-account JSON
- never commit shared private provider secrets
- never package provider credentials via `BuildConfig`, manifest placeholders,
  or resources
- Google Maps SDK for Android requires an app-level key before the map can
  initialize, so it cannot use the same in-app settings flow as private
  provider credentials
- emergency-care Places lookups use the same manifest-provided app key and do
  not store any Google credential in the Settings screen or encrypted provider
  store
- use a dedicated Android-app-restricted key for debug and another for release,
  each locked to the matching package name and SHA-1 signing certificate, with
  both Maps SDK for Android and Places API (New) enabled as needed
- user-supplied provider credentials, including the ReliefWeb app name, must be
  entered in-app after install and stored only in encrypted device-local
  storage
