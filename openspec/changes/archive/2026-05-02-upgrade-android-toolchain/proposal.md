## Why

The build toolchain has fallen significantly behind — over 18 months of version drift across JDK, Gradle, AGP, and all major dependencies. Upgrading now brings the project to current LTS tooling (JDK 25), unlocks the OpenCode Kotlin LSP (which auto-installs but works best with a current JDK), and clears the AGP compileSdk 35 cosmetic warning while reducing future upgrade debt.

## What Changes

- Install JDK 25 (Temurin) via mise, replacing the system-wide JDK 17 as the active build JDK
- Add `.mise.toml` to pin `java = "temurin-25"` for this project
- Upgrade Gradle wrapper from 8.7 → 9.5.0
- Upgrade AGP from 8.5.2 → 9.2.0 (**BREAKING**: major version jump; may have DSL/default changes)
- Upgrade Kotlin from 2.0.0 → 2.3.21
- Upgrade KSP from 2.0.0-1.0.21 → 2.3.7 (versioning scheme changed; now standalone from Kotlin)
- Upgrade Compose BOM from 2024.09.00 → 2026.04.01
- Upgrade Room from 2.6.1 → 2.8.4
- Upgrade Lifecycle from 2.8.3 → 2.10.0
- Upgrade Navigation Compose from 2.8.0 → 2.9.8
- Upgrade Core KTX from 1.13.1 → 1.18.0
- Upgrade Activity Compose from 1.9.1 → 1.13.0
- Upgrade Coroutines from 1.8.1 → 1.10.2
- Upgrade Shizuku from 13.1.5 → 13.6.0
- Bump `jvmTarget` and `sourceCompatibility`/`targetCompatibility` from Java 11 → Java 17
- Update `AGENTS.md` to reflect JDK 25 requirement

## Capabilities

### New Capabilities

None. This change is purely toolchain and dependency versioning — no new user-facing or architectural capabilities are introduced.

### Modified Capabilities

None. All existing spec-level behaviors remain unchanged. Implementation details of the build system change but no capability requirements are affected.

## Impact

- **Build files**: `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `gradle.properties`
- **New file**: `.mise.toml` (Java version pinning)
- **Documentation**: `AGENTS.md` (JDK version requirement update)
- **No app source changes expected** — unless Shizuku 13.6.0 changed its API surface in a way that affects `ShizukuHelper.kt` or `BluetoothBouncerUserService.kt` (to be verified)
- **Risk**: AGP 9.x is a major version jump; KSP versioning scheme changed. Build verification is the primary validation step.
