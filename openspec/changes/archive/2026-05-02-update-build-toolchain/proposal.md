## Why

The build toolchain has drifted out of sync: the Dockerfile still targets JDK 17 / jammy / android-35 while the project now requires JDK 25 / android-36. Additionally, `mise.toml` is unnecessary since mise natively reads `.java-version`, a more universal convention. These misalignments mean Docker builds are broken and the local setup instructions are more tool-specific than they need to be.

## What Changes

- Replace `mise.toml` with `.java-version` (content: `temurin-25`) — supported natively by mise, jenv, SDKMAN, and IntelliJ
- Update Dockerfile base image from `eclipse-temurin:17-jdk-jammy` to `eclipse-temurin:25-jdk-noble`
- Update Dockerfile Android SDK packages: `platforms;android-35` → `platforms;android-36`, `build-tools;35.0.0` → `build-tools;36.0.0`
- Bump `ANDROID_CMDLINE_TOOLS_VERSION` from `11076708` (v13.0) to `14742923` (v20.0)
- Update `AGENTS.md` to remove the mise-specific instruction and reference `.java-version` instead

## Capabilities

### New Capabilities

_(none — this is a toolchain version update, no new user-facing capabilities)_

### Modified Capabilities

- `build-toolchain`: JDK version pinning mechanism changes from `mise.toml` to `.java-version`; AGENTS.md documentation updated accordingly
- `docker-build`: Docker base image updates from JDK 17/jammy to JDK 25/noble; Android SDK packages updated to API 36

## Impact

- `mise.toml` deleted; `.java-version` added
- `Dockerfile` updated (base image, cmdline-tools version, platform SDK, build-tools)
- `AGENTS.md` updated (Build & Tooling section)
- No application code changes; no user-facing behavior changes
- Docker image layer cache will be invalidated on first rebuild after this change
