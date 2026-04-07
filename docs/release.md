# Release Workflow

Last updated: 2026-04-07

## Release Strategy

The Android port uses a local-first release process similar to the macOS repo:

- tracked version metadata in `Config/version.properties`
- gitignored local signing and Play credentials
- helper scripts in `scripts/`
- Internal testing as the first Google Play target

## Required Local Files

Create these local files from examples:

- `Config/Signing.env`
- `Config/AppConfig.env` only if you use local helper scripts such as source
  probes; it is not used to package shipped app credentials

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

From `Config/AppConfig.env` for optional helper scripts only:
- `NOMAD_RELIEFWEB_APP_NAME`

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

## Current Status

Implemented:
- version source of truth
- release helper scripts
- publish helper wiring
- shipped app provider credentials removed from build-time env injection

Not yet verified with real credentials:
- signed release artifact generation
- internal-track upload
- version bump flow with a populated remote repository

## Security Rules

- never commit keystores
- never commit Play service-account JSON
- never commit shared private provider secrets
- never package provider credentials via `BuildConfig`, manifest placeholders,
  or resources
- user-supplied provider credentials must be entered in-app after install and
  stored only in encrypted device-local storage
