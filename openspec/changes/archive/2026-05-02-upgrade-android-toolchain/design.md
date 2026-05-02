## Context

The project currently builds on JDK 17 (system-installed Temurin), Gradle 8.7, and AGP 8.5.2 — all roughly 18 months behind current stable releases. The immediate catalyst is enabling the OpenCode Kotlin LSP, which auto-installs for Kotlin projects but benefits from a current JDK. The broader goal is closing the version drift across the full toolchain: AGP, Gradle, Kotlin, KSP, and all AndroidX/Compose dependencies.

JDK version management will move from a system-wide install to mise, using a `.mise.toml` file pinned at the project root. This makes the JDK version explicit, reproducible, and portable across machines.

## Goals / Non-Goals

**Goals:**
- Move JDK to 25 (current LTS) via mise/Temurin
- Upgrade Gradle to 9.5.0, AGP to 9.2.0
- Upgrade Kotlin to 2.3.21, KSP to 2.3.7 (KSP2 standalone)
- Upgrade all AndroidX/Compose/Room/Shizuku dependencies to current stable
- Bump `jvmTarget` and `sourceCompatibility`/`targetCompatibility` to Java 17
- Keep a clean, verified build after each logical step

**Non-Goals:**
- No app source changes (unless forced by API breakage)
- No migration to KMP (Room 2.8 supports it but we're not adopting it)
- No new features or capability changes
- Not uninstalling the existing system JDK 17 (that's a manual step for the user)

## Decisions

### JDK 25 via mise, not system-wide

**Decision**: Use `mise use java@temurin-25` to install and pin JDK 25 at the project level via `.mise.toml`, rather than a new system-wide install.

**Rationale**: Mise is already in use on this machine. A `.mise.toml` file at the project root makes the JDK version part of the repo — anyone cloning the project gets the right JDK automatically. System-wide installs are harder to manage across multiple projects with differing requirements.

**Alternative considered**: Install JDK 25 as a system-wide Temurin package and update `JAVA_HOME` manually. Rejected: fragile across shell sessions, not reproducible.

### Jump directly to AGP 9.x (not step through 8.x)

**Decision**: Upgrade directly from AGP 8.5.2 → 9.2.0, skipping the 8.x latest (8.13.2).

**Rationale**: This project is small (18 Kotlin files, single module, no custom Gradle plugins). The incremental safety of stepping through 8.x latest adds overhead with little benefit. AGP 9.x requires Gradle 9.x anyway, so stepping through 8.x would still require two separate Gradle wrapper upgrades.

**Alternative considered**: Step through AGP 8.13.2 + Gradle 8.14.4 first. Rejected: unnecessary for project complexity, doubles the work.

### jvmTarget → Java 17 (not 21 or 25)

**Decision**: Set `jvmTarget = "17"` and `sourceCompatibility = JavaVersion.VERSION_17`, even though the build JDK will be 25.

**Rationale**: The JDK version (25) governs what runs Gradle and the compiler. The `jvmTarget` governs the bytecode emitted into the APK. Android's D8/R8 desugaring handles Java 17 bytecode on `minSdk = 31` (Android 12) without issues. Java 21+ bytecode targets are less battle-tested on Android and offer no practical benefit for this project's code.

**Alternative considered**: `jvmTarget = "21"`. Not rejected outright, but conservative choice given Shizuku's heavy reflection usage — Java 17 is the safer bet.

### KSP: no explicit migration to KSP2 API

**Decision**: Simply update the KSP version to 2.3.7. Do not explicitly migrate Room annotation processor usage to KSP2's new standalone API.

**Rationale**: Room 2.8.4 handles KSP2 transparently when the KSP Gradle plugin is updated. KSP2's API changes are internal to the plugin infrastructure; Room's `@Dao`, `@Entity`, `@Database` annotations work unchanged.

### Verify Shizuku API surface before and after

**Decision**: Read `ShizukuHelper.kt` and `BluetoothBouncerUserService.kt` before and after bumping Shizuku 13.1.5 → 13.6.0 to confirm no API changes affect the reflection-heavy hidden API code.

**Rationale**: Shizuku 13.x versions have occasionally shifted how the UserService lifecycle or binder handoff works. The existing code uses non-trivial reflection patterns (`ServiceManager.getService`, `BluetoothAdapter` hidden constructor, `sAdapter` field injection). A silent API change here would produce a runtime failure rather than a compile error.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| AGP 9.x changed a DSL or default that breaks the build | Build immediately after AGP/Gradle bump; check AGP 9.0 migration guide for any renamed DSL elements |
| Shizuku 13.6.0 changed UserService or binder APIs | Read release notes and diff the Shizuku API before bumping; test on physical device after |
| Gradle 9.x dropped a property or behaviour relied on in `gradle.properties` | Verify `org.gradle.configuration-cache=true` and other properties still valid in Gradle 9 docs |
| JDK 25 has a compatibility issue with Gradle 9.5 or AGP 9.2 | Both shipped after JDK 25 GA (Sep 2025); low risk. Fallback: use JDK 21 via `mise use java@temurin-21` |
| KSP 2.3.7 + Room 2.8.4 annotation processing regression | Run `assembleDebug` after; Room generates DAOs at compile time so failures are immediately visible |

## Migration Plan

Apply changes in this order to isolate failures:

1. **JDK first** — Install JDK 25 via mise, verify `java -version`, set `ANDROID_HOME`, run existing build to confirm JDK 25 is compatible with current Gradle 8.7/AGP 8.5.2 before anything else changes.
2. **Gradle + AGP together** — Update `gradle-wrapper.properties` to Gradle 9.5.0 and `libs.versions.toml` AGP to 9.2.0 in one step (they are coupled). Build and fix any DSL issues.
3. **Kotlin + KSP** — Bump Kotlin to 2.3.21 and KSP to 2.3.7. Build.
4. **AndroidX / Compose / Room / Shizuku** — Bump remaining dependencies. Build.
5. **jvmTarget bump** — Update `app/build.gradle.kts` compile options to Java 17. Build.
6. **Documentation** — Update `AGENTS.md` and add `.mise.toml`.

**Rollback**: Each step is a small diff. If a step breaks the build and can't be resolved quickly, revert that step's `libs.versions.toml` / `gradle-wrapper.properties` hunk and file an issue.

## Open Questions

- Does Shizuku 13.6.0 have any API changes affecting `BluetoothBouncerUserService.kt`? Check Shizuku release notes before implementation.
- Does Gradle 9.x still support `org.gradle.configuration-cache=true` with the same semantics, or does it require migration to the new configuration cache model? Check Gradle 9 release notes.
