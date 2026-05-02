## Context

Building the app currently requires JDK 17, the Android SDK (platform-tools, platforms;android-35, build-tools), and a configured `local.properties` pointing to the SDK location. This ties builds to a specific machine configuration. The goal is to encapsulate the entire build environment in a Docker image so any machine with Docker can produce an APK, and so GitHub Actions CI can use the same build path.

The project uses:
- Gradle 8.7 (via wrapper, downloaded at build time)
- AGP 8.5.2
- Kotlin 2.0.0, KSP 2.0.0-1.0.21
- compileSdk / targetSdk 35
- JVM target 11, JDK 17 required

## Goals / Non-Goals

**Goals:**
- Single `Dockerfile` that produces a debug APK (release-configurable via build arg)
- Layer cache structure that avoids re-downloading SDK/deps on source-only changes
- APK extracted to the host via BuildKit `--output`, no running container required
- `.dockerignore` that excludes all host-specific and generated files

**Non-Goals:**
- Dev container / interactive shell environment
- Emulator or instrumented test execution inside Docker
- Pushing the image to any registry
- Release signing (no signing config exists today)

## Decisions

### 1. Base image: `eclipse-temurin:17-jdk-jammy`

**Chosen over** pre-built Android SDK images (e.g., `thyrlian/android-sdk`, `cimg/android`).

**Rationale**: Pre-built community images may go unmaintained. Eclipse Temurin is an Adoptium project with a long support commitment, and `jammy` (Ubuntu 22.04 LTS) is stable. Rolling our own SDK install from Google's stable `cmdline-tools` URL keeps the image self-contained and auditable.

### 2. Layer order for cache efficiency

```
Layer 1  eclipse-temurin:17-jdk-jammy          ← changes: never
Layer 2  Android cmdline-tools + sdkmanager    ← changes: yearly (SDK version bump)
         install: platform-tools,
                  platforms;android-35,
                  build-tools;35.0.0
Layer 3  COPY gradle wrapper + build configs   ← changes: rarely (dep/plugin updates)
         (gradlew, gradle/, *.gradle.kts,
          gradle.properties, settings.gradle.kts)
Layer 4  RUN ./gradlew dependencies            ← changes: when deps change
         (pre-warms Gradle + Maven cache)
Layer 5  COPY source + RUN assembleDebug       ← changes: every build
```

Layers 1–4 are cached across source-only changes, making rebuild time roughly 1–2 minutes after the first run (which downloads ~1.5 GB).

### 3. Multi-stage build with BuildKit `--output`

A single `builder` stage does everything. The final `COPY --from=builder` extracts only the APK. Using `--output type=local,dest=out` means no image needs to be tagged or stored — the APK lands in `./out/` on the host.

```
docker build --output=out .
```

**Chosen over** `docker create` + `docker cp`: cleaner, stateless, no container cleanup required.

### 4. `BUILD_TYPE` build arg (default: `debug`)

```dockerfile
ARG BUILD_TYPE=debug
RUN ./gradlew assemble${BUILD_TYPE^}
```

Allows `docker build --build-arg BUILD_TYPE=release --output=out .` without any Dockerfile edits. Default is `debug` since no release signing is configured.

### 5. `local.properties` replaced by `ANDROID_HOME` env var

The image sets `ENV ANDROID_HOME=/opt/android-sdk`. Gradle reads this env var directly; `local.properties` is not needed and is excluded via `.dockerignore`.

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Image is large (~2–3 GB) | Ephemeral — never pushed to a registry. CI layer caching (GitHub Actions cache backend) keeps CI fast after first run. |
| Google's `cmdline-tools` download URL changes | URL is versioned; pin to a specific version. Update when needed (rare). |
| Gradle configuration cache may be stale across builds | `--no-configuration-cache` flag can be added if cache corruption occurs; or exclude `.gradle/configuration-cache` from the layer. |
| `build-tools` version may need updating with AGP upgrades | `build-tools;35.0.0` is pinned in the `sdkmanager` install command; update alongside AGP. |
| License acceptance is automated (`yes \| sdkmanager --licenses`) | Acceptable for a personal/CI build; licenses are Apache 2.0 / standard Android SDK terms. |

## Open Questions

- None — scope is well-defined and all decisions are made.
