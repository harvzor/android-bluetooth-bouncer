## MODIFIED Requirements

### Requirement: Configurable build type
The `Dockerfile` SHALL support a `BUILD_TYPE` build argument accepting `debug` or `release`, defaulting to `debug`.

#### Scenario: Default build produces a debug APK
- **WHEN** `docker build --output=out .` is run without specifying `BUILD_TYPE`
- **THEN** `app-debug.apk` is present in `./out/`

#### Scenario: Release build type is selectable
- **WHEN** `docker build --build-arg BUILD_TYPE=release --output=out .` is run
- **THEN** Gradle runs `assembleRelease` inside the container

## ADDED Requirements

### Requirement: Optional VERSION build argument forwarded to Gradle
The `Dockerfile` SHALL accept an optional `VERSION` build argument. When provided, it SHALL be forwarded to the Gradle assemble command as `-PappVersionName=<VERSION>`. When absent or empty, no `-PappVersionName` flag SHALL be passed, allowing `build.gradle.kts` to apply its dev fallback.

#### Scenario: VERSION arg present is forwarded to Gradle
- **WHEN** `docker build --build-arg VERSION=1.2.3 --output=out .` is run
- **THEN** Gradle is invoked with `-PappVersionName=1.2.3`
- **THEN** the APK's `versionName` is `"1.2.3"`

#### Scenario: VERSION arg absent leaves Gradle property unset
- **WHEN** `docker build --output=out .` is run without `VERSION`
- **THEN** Gradle is invoked without `-PappVersionName`
- **THEN** the APK's `versionName` falls back to `"0.0.0-dev"`
