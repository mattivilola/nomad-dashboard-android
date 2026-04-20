# Release Workflow

Last updated: 2026-04-20

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

Optional Play publishing override:
- `NOMAD_PLAY_RELEASE_STATUS`
  - defaults to `draft` so the first internal-track upload works while the Play
    app is still in draft state
  - set to `completed` after the app is no longer draft and you want uploads to
    auto-complete the internal release
- `NOMAD_PLAY_CLOSED_TRACK`
  - optional closed-testing track name for `make publish-closed`
  - defaults to `closed`; set it if your Play Console closed track uses a
    different custom name
  - if Play returns `Track not found: closed`, your closed track likely has a
    custom name and this env var must match that exact Play Console track name

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

Current dry-run output also prints `CLOSED_TRACK`, which is the exact value
`make publish-closed` will use.
`make publish-closed` resolves that value after loading local env files such as
`Config/Signing.env`.

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

Upload to closed testing:

```sh
make publish-closed
```

Upload to production:

```sh
make publish-production
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
- release builds now request native symbol-table extraction for bundled `.so`
  libraries via AGP so Play Console native crash symbolication is enabled where
  dependency-provided native debug metadata is available, without changing
  runtime behavior
- Play publishing defaults the release status to `draft` for first-time
  internal testing on a draft Play app; override with
  `NOMAD_PLAY_RELEASE_STATUS=completed` after the app leaves draft state
- Play publishing now has explicit helper targets for `internal`, `closed`,
  and `production` tracks so releases do not require manual env edits before
  each upload

Recommended next steps before production:
- keep using `make publish-closed` until the app survives real tester usage and
  the onboarding, permissions, provider configuration, and Play declarations
  are stable
- verify the Play-installed release with a fresh tester install, especially
  location permission onboarding, Maps/Places behavior, and any encrypted local
  provider credential flows
- complete the remaining Play Console setup items for production such as Data
  safety, content rating, privacy policy, and foreground-service declarations
- use `make publish-production` only when the Play release status should be
  `completed` or when you intentionally override it for a staged rollout

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
- release build configured to request native symbol-table extraction for Play
  Console crash reporting where dependency-provided native debug metadata is
  available
- Play publishing now defaults to `draft` release status so first internal
  uploads can succeed on a draft Play app, with an env override for later
  `completed` uploads
- Play publishing helper targets now exist for internal, closed, and
  production tracks

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
