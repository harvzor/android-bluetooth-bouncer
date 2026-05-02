## ADDED Requirements

### Requirement: Project pins JDK version via mise
The project SHALL include a `.mise.toml` file at the repository root that pins the Java version to `temurin-25`, ensuring all developers and CI environments use the same JDK without manual JAVA_HOME configuration.

#### Scenario: Developer clones repo with mise installed
- **WHEN** a developer with mise installed clones the repository and runs any `gradlew` command
- **THEN** mise automatically resolves JDK 25 (Temurin) as the active Java runtime for that directory

#### Scenario: Missing JDK handled by mise
- **WHEN** a developer runs `gradlew` and JDK 25 is not yet installed locally
- **THEN** mise installs `temurin-25` before the build proceeds

### Requirement: Build uses Gradle 9.x and AGP 9.x
The project build system SHALL use Gradle 9.5.0 and Android Gradle Plugin 9.2.0 as defined in `gradle-wrapper.properties` and `libs.versions.toml` respectively.

#### Scenario: Gradle wrapper resolves correct version
- **WHEN** `gradlew assembleDebug` is executed
- **THEN** Gradle 9.5.0 is used as the build system

#### Scenario: Build succeeds with updated AGP
- **WHEN** `gradlew assembleDebug` is executed after the upgrade
- **THEN** the build completes without errors and produces a valid APK

### Requirement: JVM bytecode targets Java 17
The project SHALL compile Kotlin source to JVM 17 bytecode, with `sourceCompatibility`, `targetCompatibility`, and `kotlinOptions.jvmTarget` all set to Java 17.

#### Scenario: Compiled bytecode targets Java 17
- **WHEN** the project is compiled
- **THEN** output bytecode is compatible with Java 17 class file format

### Requirement: AGENTS.md documents current JDK requirement
The `AGENTS.md` file SHALL accurately document JDK 25 as the required Java version for building this project, replacing the previous JDK 17 requirement.

#### Scenario: Developer reads AGENTS.md for environment setup
- **WHEN** a developer reads AGENTS.md to set up their environment
- **THEN** the documented JDK requirement matches the version pinned in `.mise.toml`
