## 1. Install JDK 25 via mise

- [x] 1.1 Run `mise use java@temurin-25` in the project root to install JDK 25 and create `.mise.toml`
- [x] 1.2 Verify `java -version` reports `25.x.x` (Temurin)
- [x] 1.3 Verify `$env:JAVA_HOME` resolves to the mise-managed JDK 25 path
- [x] 1.4 Run `gradlew.bat assembleDebug` (with existing Gradle 8.7 / AGP 8.5.2) to confirm JDK 25 is compatible before any other changes

## 2. Upgrade Gradle and AGP

- [x] 2.1 Update `gradle/wrapper/gradle-wrapper.properties`: set `distributionUrl` to `https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip`
- [x] 2.2 Update `gradle/libs.versions.toml`: set `agp = "9.2.0"`
- [x] 2.3 Check Gradle 9 release notes for any removed/renamed properties — verify `org.gradle.configuration-cache=true` in `gradle.properties` is still valid
- [x] 2.4 Run `gradlew.bat assembleDebug` and fix any AGP 9.x DSL or API errors

## 3. Upgrade Kotlin and KSP

- [x] 3.1 Update `gradle/libs.versions.toml`: set `kotlin = "2.3.21"` and `ksp = "2.3.7"`
- [x] 3.2 Run `gradlew.bat assembleDebug` and fix any Kotlin 2.x compilation errors

## 4. Upgrade AndroidX, Compose, Room, and Shizuku

- [x] 4.1 Read Shizuku 13.6.0 release notes and check for API changes affecting `ShizukuHelper.kt` or `BluetoothBouncerUserService.kt`
- [x] 4.2 Update `gradle/libs.versions.toml` with all remaining dependency versions:
  - `composeBom = "2026.04.01"`
  - `room = "2.8.4"`
  - `lifecycleRuntimeKtx = "2.10.0"`
  - `navigationCompose = "2.9.8"`
  - `coreKtx = "1.18.0"`
  - `activityCompose = "1.13.0"`
  - `coroutines = "1.10.2"`
  - `shizuku = "13.6.0"`
- [x] 4.3 Run `gradlew.bat assembleDebug` and fix any dependency API errors
- [x] 4.4 If Shizuku API changes require source updates, apply minimal fixes to `ShizukuHelper.kt` and/or `BluetoothBouncerUserService.kt`

## 5. Bump JVM Bytecode Target

- [x] 5.1 Update `app/build.gradle.kts`: set `sourceCompatibility = JavaVersion.VERSION_17`, `targetCompatibility = JavaVersion.VERSION_17`, and `kotlinOptions { jvmTarget = "17" }`
- [x] 5.2 Run `gradlew.bat assembleDebug` to confirm clean build with Java 17 bytecode target

## 6. Update Documentation

- [x] 6.1 Update `AGENTS.md`: replace JDK 17 references with JDK 25; update the mise install command; remove the AGP compileSdk 35 warning note if no longer applicable
- [x] 6.2 Verify `.mise.toml` is present at repo root and contains `java = "temurin-25"` (or equivalent mise format)

## 7. Final Verification

- [x] 7.1 Run `gradlew.bat assembleDebug` from a clean state (`gradlew.bat clean assembleDebug`) to confirm full build succeeds
- [x] 7.2 Confirm `java -version` reports JDK 25 in the project directory
- [ ] 7.3 Open project in OpenCode and verify Kotlin LSP activates (diagnostics appear for `.kt` files)
