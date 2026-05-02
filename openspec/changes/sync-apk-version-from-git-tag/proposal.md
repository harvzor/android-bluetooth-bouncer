## Why

The release workflow publishes APKs tagged as `v*` on GitHub, but the APK's internal `versionName` and `versionCode` are hardcoded in `app/build.gradle.kts`. This means every release ships an APK that reports version `1.0` regardless of the git tag, making in-app version displays and update-ordering unreliable.

## What Changes

- The GitHub Actions release workflow extracts the semver string from the pushed tag and forwards it to the Docker build
- The Dockerfile accepts a new `VERSION` build argument and passes it to Gradle
- `app/build.gradle.kts` reads the version from a Gradle property, derives `versionName` and `versionCode`, and falls back to `0.0.0-dev` / `1` for local builds

## Capabilities

### New Capabilities

- `apk-versioning`: How the APK's `versionName` and `versionCode` are derived from a Gradle property at build time, including the semver-to-integer derivation and the dev fallback

### Modified Capabilities

- `github-action-release-build`: The workflow now extracts the semver string from the tag ref and passes it as a `VERSION` build argument to `docker build`
- `docker-build`: The Dockerfile now accepts an optional `VERSION` build argument and forwards it to Gradle via `-PappVersionName`

## Impact

- `app/build.gradle.kts` — versionName and versionCode configuration
- `Dockerfile` — new `VERSION` ARG, forwarded to Gradle invocation
- `.github/workflows/release-build.yml` — tag extraction step, additional `--build-arg`
