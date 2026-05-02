## Why

Building the app today requires Android Studio, the Android SDK, and JDK 17 configured on the host machine — a non-trivial setup that is fragile to replicate in the future or on a new machine. Dockerizing the build makes the app buildable from any machine with only Docker installed, and creates a foundation for CI via GitHub Actions.

## What Changes

- New `Dockerfile` (multi-stage) that installs JDK 17, Android SDK, and builds the APK
- New `.dockerignore` to exclude host-specific and ephemeral files from the build context
- APK extracted from the image via `--output` (BuildKit), no container left behind
- `local.properties` excluded from the build; `ANDROID_HOME` set via environment inside the image

## Capabilities

### New Capabilities

- `docker-build`: Hermetic Android APK build via Docker — installs JDK 17, Android SDK cmdline-tools, and the required SDK packages inside a Docker image, then builds the APK using the Gradle wrapper. APK is extracted to the host via BuildKit `--output`. Supports a `BUILD_TYPE` build arg (`debug` / `release`, default `debug`). No Android SDK or JDK required on the host.

### Modified Capabilities

## Impact

- **New files**: `Dockerfile`, `.dockerignore`
- **No source changes**: App code is unchanged
- **`local.properties`**: Must be excluded from Docker context (already gitignored); `sdk.dir` is replaced by `ANDROID_HOME` inside the image
- **Dependencies**: Docker (with BuildKit, default since Docker 23) is the only host requirement
- **Image size**: ~2–3 GB (ephemeral; never pushed to a registry)
- **Future CI**: The same `Dockerfile` will be used by a GitHub Actions workflow to build and upload the APK as an artifact
