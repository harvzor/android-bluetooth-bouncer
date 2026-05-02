## 1. Build Context Exclusions

- [x] 1.1 Create `.dockerignore` excluding `local.properties`, `.gradle/`, `build/`, `app/build/`, `.idea/`, `.kotlin/`, `.git/`, and `*.iml`

## 2. Dockerfile

- [x] 2.1 Create `Dockerfile` using `eclipse-temurin:17-jdk-jammy` as base
- [x] 2.2 Install Android `cmdline-tools` (download from Google, unzip to `/opt/android-sdk/cmdline-tools/latest/`)
- [x] 2.3 Set `ANDROID_HOME=/opt/android-sdk` and add `cmdline-tools/latest/bin` and `platform-tools` to `PATH`
- [x] 2.4 Accept SDK licenses and install `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` via `sdkmanager`
- [x] 2.5 Copy Gradle wrapper and build config files (`gradlew`, `gradlew.bat`, `gradle/`, `*.gradle.kts`, `gradle.properties`, `settings.gradle.kts`) before source to create a cacheable dependency layer
- [x] 2.6 Run `./gradlew dependencies` to pre-warm the Gradle and Maven dependency cache as a separate layer
- [x] 2.7 Copy full project source
- [x] 2.8 Add `ARG BUILD_TYPE=debug` and run `./gradlew assemble${BUILD_TYPE^}` (capitalised via shell)
- [x] 2.9 Add a final scratch/export stage that `COPY --from=builder` the APK output path so `--output` extracts only the APK

## 3. Verification

- [x] 3.1 Run `docker build --output=out .` on a clean Docker environment and confirm `out/app-debug.apk` is produced
- [x] 3.2 Confirm no stopped containers are left after the build
- [x] 3.3 Modify a source file, re-run the build, and confirm layers 1–4 are cached (only the final assemble step re-runs)
- [x] 3.4 Run `docker build --build-arg BUILD_TYPE=release --output=out .` and confirm Gradle runs `assembleRelease`
