## Context

The project's build toolchain has two components that were out of sync with the current project requirements:

1. **Local JDK pinning**: `mise.toml` pins `java = "temurin-25"`. Mise also supports `.java-version` natively (in addition to `mise.toml`), and `.java-version` is a more universal convention understood by jenv, SDKMAN, IntelliJ, and other tooling. The `mise.toml` file added a mise-specific indirection for something that can be expressed more simply.

2. **Dockerfile**: The base image (`eclipse-temurin:17-jdk-jammy`) and Android SDK packages (`platforms;android-35`, `build-tools;35.0.0`) are stale. The project targets `compileSdk = 36` and uses Gradle 9.5.0 / AGP 9.2.0, both of which require JDK 21+ at minimum. The current Dockerfile would fail immediately on `./gradlew` invocation.

## Goals / Non-Goals

**Goals:**
- Dockerfile produces a working build with no host dependencies beyond Docker
- JDK version pinning works with mise and other common version managers
- AGENTS.md accurately describes the setup without tool-specific prescriptions

**Non-Goals:**
- Changing the Dockerfile architecture or layer structure
- Multi-arch Docker support
- Updating Gradle, AGP, Kotlin, or any application dependencies
- Changing the JVM bytecode target (remains Java 17 — the toolchain runs on JDK 25, but compiles to JVM 17 bytecode)

## Decisions

### 1. `.java-version` over `mise.toml`

**Decision**: Replace `mise.toml` with `.java-version` containing `temurin-25`.

**Rationale**: Mise reads `.java-version` natively. The file is also understood by jenv and SDKMAN, making it tool-agnostic. `mise.toml` is not wrong, but it's more specific than needed when the only entry is the Java version.

**Alternatives considered**: Keeping `mise.toml` — rejected because it implies mise is required, when `.java-version` works with more tools.

### 2. `eclipse-temurin:25-jdk-noble` as Docker base image

**Decision**: Update base image from `eclipse-temurin:17-jdk-jammy` to `eclipse-temurin:25-jdk-noble`.

**Rationale**: JDK 25 is required to run Gradle 9.5.0 and AGP 9.2.0. Ubuntu noble (24.04) is the current LTS and the `temurin:25` tag is available for it. Ubuntu jammy (22.04) does not have a `temurin:25` image.

**Alternatives considered**: `temurin:21-jdk-noble` (minimum viable for Gradle 9.x) — rejected to stay consistent with the local development JDK pinned in `.java-version`.

### 3. `build-tools;36.0.0` (not `36.1.0`)

**Decision**: Use `build-tools;36.0.0` in the Dockerfile.

**Rationale**: `36.0.0` is the stable release matching `compileSdk = 36`. `36.1.0` is also available but introduces no known requirement for this project. Using `36.0.0` keeps the Dockerfile aligned with the compile SDK major version and avoids pulling a newer version than tested locally.

### 4. `cmdline-tools` revision `14742923` (v20.0)

**Decision**: Bump `ANDROID_CMDLINE_TOOLS_VERSION` from `11076708` (v13.0) to `14742923` (v20.0).

**Rationale**: This is the latest stable cmdline-tools release per Google's SDK repository manifest. It includes `sdkmanager` capable of resolving `android-36` packages.

## Risks / Trade-offs

- **Docker layer cache invalidated**: The base image change will invalidate all cached layers on first rebuild. This is a one-time cost with no ongoing impact.
- **JDK toolchain auto-download**: `app/build.gradle.kts` sets `kotlin { jvmToolchain(17) }`, which could trigger Gradle's toolchain resolver to auto-download JDK 17 inside the container (since the container only has JDK 25). In practice, Kotlin's JVM toolchain uses `--release 17` cross-compilation rather than requiring a separate JDK 17 installation, so this should not be an issue — but it's worth verifying on first build.

## Migration Plan

No deployment or data migration required. This is a build environment change:

1. Delete `mise.toml`, create `.java-version`
2. Update `Dockerfile` versions in-place
3. Update `AGENTS.md` documentation
4. Verify Docker build produces a valid APK (`docker build --output=out .`)
5. Verify local build still works (`gradlew assembleDebug`)

Rollback: revert the three file changes. No persistent state is affected.
