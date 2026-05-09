### Requirement: Hermetic APK build via Docker
The system SHALL produce a debug APK using only Docker as a host dependency. The build environment (JDK 25, Android SDK API 36, Gradle) SHALL be fully encapsulated in a `Dockerfile` at the project root using `eclipse-temurin:25-jdk-noble` as the base image. No Android SDK, JDK, or Gradle installation SHALL be required on the host.

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

### Requirement: Release APK signed via BuildKit secret mounts
When signing credentials are provided, the `Dockerfile` SHALL sign the release APK during the Gradle build. The keystore SHALL be passed as a BuildKit secret mount and SHALL NOT appear in any image layer. Passwords SHALL also be passed as BuildKit secret mounts using the `env=` form and SHALL NOT be declared as `ARG` or `ENV` instructions in the Dockerfile.

#### Scenario: Signed release APK produced when secrets provided
- **WHEN** `docker build` is run with `--secret id=keystore,src=./release.keystore`, `--secret id=store_password,env=RELEASE_STORE_PASSWORD`, `--secret id=key_alias,env=RELEASE_KEY_ALIAS`, and `--secret id=key_password,env=RELEASE_KEY_PASSWORD`
- **THEN** Gradle signs the APK during assembly
- **THEN** the output APK is signed and passes `apksigner verify`

#### Scenario: Unsigned APK produced when secrets absent
- **WHEN** `docker build` is run without signing secrets
- **THEN** the output APK is unsigned (suitable for local debug builds)

#### Scenario: Keystore and passwords do not appear in image layers
- **WHEN** the Docker build completes
- **THEN** no `ARG` or `ENV` instruction for signing credentials exists in the Dockerfile
- **THEN** the keystore and passwords are only accessible within the `RUN` step that mounts them

### Requirement: Output APK named with version when VERSION is set
When a `VERSION` build argument is provided, the output APK SHALL be named `bluetooth-bouncer-<VERSION>.apk`. When `VERSION` is absent, the original Gradle output filename SHALL be preserved.

#### Scenario: Versioned output filename
- **WHEN** `docker build --build-arg VERSION=1.0.0-rc1 --output=out .` is run
- **THEN** the APK in `./out/` is named `bluetooth-bouncer-1.0.0-rc1.apk`

#### Scenario: Default output filename when VERSION absent
- **WHEN** `docker build --output=out .` is run without `VERSION`
- **THEN** the APK in `./out/` retains the Gradle default filename (e.g. `app-debug.apk`)

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
