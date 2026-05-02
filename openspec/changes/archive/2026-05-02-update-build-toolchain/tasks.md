## 1. JDK Version Pinning

- [x] 1.1 Delete `mise.toml` from the repository root
- [x] 1.2 Create `.java-version` at the repository root with content `temurin-25`

## 2. AGENTS.md Documentation

- [x] 2.1 Update the Java entry in `AGENTS.md` Build & Tooling section to reference `.java-version` instead of `mise.toml`, removing the mise-specific install instruction

## 3. Dockerfile Updates

- [x] 3.1 Update base image from `eclipse-temurin:17-jdk-jammy` to `eclipse-temurin:25-jdk-noble`
- [x] 3.2 Bump `ANDROID_CMDLINE_TOOLS_VERSION` ARG from `11076708` to `14742923`
- [x] 3.3 Update sdkmanager package `platforms;android-35` to `platforms;android-36`
- [x] 3.4 Update sdkmanager package `build-tools;35.0.0` to `build-tools;36.0.0`

## 4. Verification

- [x] 4.1 Run `docker build --output=out .` and confirm a valid APK is produced in `./out/`
- [x] 4.2 Run `gradlew assembleDebug` locally and confirm the build succeeds
