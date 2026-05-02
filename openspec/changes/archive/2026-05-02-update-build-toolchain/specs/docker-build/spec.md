## MODIFIED Requirements

### Requirement: Hermetic APK build via Docker
The system SHALL produce a debug APK using only Docker as a host dependency. The build environment (JDK 25, Android SDK API 36, Gradle) SHALL be fully encapsulated in a `Dockerfile` at the project root using `eclipse-temurin:25-jdk-noble` as the base image. No Android SDK, JDK, or Gradle installation SHALL be required on the host.

#### Scenario: Build succeeds with only Docker installed
- **WHEN** a user runs `docker build --output=out .` from the project root on a machine with Docker (BuildKit-capable) and no other Android tooling
- **THEN** the build completes successfully and an APK file is written to `./out/`

#### Scenario: Build context excludes host-specific files
- **WHEN** Docker builds the image
- **THEN** `local.properties`, `.gradle/`, `build/`, `.idea/`, and `.git/` are excluded from the build context via `.dockerignore`
