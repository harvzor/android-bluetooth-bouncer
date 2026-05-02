## ADDED Requirements

### Requirement: Hermetic APK build via Docker
The system SHALL produce a debug APK using only Docker as a host dependency. The build environment (JDK 17, Android SDK, Gradle) SHALL be fully encapsulated in a `Dockerfile` at the project root. No Android SDK, JDK, or Gradle installation SHALL be required on the host.

#### Scenario: Build succeeds with only Docker installed
- **WHEN** a user runs `docker build --output=out .` from the project root on a machine with Docker (BuildKit-capable) and no other Android tooling
- **THEN** the build completes successfully and an APK file is written to `./out/`

#### Scenario: Build context excludes host-specific files
- **WHEN** Docker builds the image
- **THEN** `local.properties`, `.gradle/`, `build/`, `.idea/`, and `.git/` are excluded from the build context via `.dockerignore`

### Requirement: Configurable build type
The `Dockerfile` SHALL support a `BUILD_TYPE` build argument accepting `debug` or `release`, defaulting to `debug`.

#### Scenario: Default build produces a debug APK
- **WHEN** `docker build --output=out .` is run without specifying `BUILD_TYPE`
- **THEN** `app-debug.apk` is present in `./out/`

#### Scenario: Release build type is selectable
- **WHEN** `docker build --build-arg BUILD_TYPE=release --output=out .` is run
- **THEN** Gradle runs `assembleRelease` inside the container

### Requirement: Layer cache optimised for source-only changes
The `Dockerfile` SHALL order layers so that Android SDK installation and Gradle dependency resolution are cached independently of application source changes.

#### Scenario: Source-only rebuild uses cached layers
- **WHEN** only files under `app/src/` are changed and `docker build --output=out .` is re-run
- **THEN** Docker reuses cached layers for the SDK install and dependency resolution steps, and only the final compile-and-assemble layer is re-executed

### Requirement: APK extracted without a running container
The build SHALL use BuildKit multi-stage output (`--output type=local,dest=out`) so the APK is written directly to the host filesystem with no container left running or requiring manual cleanup.

#### Scenario: No container artifact after build
- **WHEN** `docker build --output=out .` completes
- **THEN** no stopped or running container exists as a side-effect of the build
