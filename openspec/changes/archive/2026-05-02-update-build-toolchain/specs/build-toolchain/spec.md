## MODIFIED Requirements

### Requirement: Project pins JDK version via .java-version
The project SHALL include a `.java-version` file at the repository root containing `temurin-25`, ensuring developers and CI environments using mise, jenv, SDKMAN, or compatible tooling resolve the correct JDK automatically.

#### Scenario: Developer clones repo with mise installed
- **WHEN** a developer with mise installed clones the repository and runs any `gradlew` command
- **THEN** mise automatically resolves JDK 25 (Temurin) as the active Java runtime for that directory

#### Scenario: Missing JDK handled by mise
- **WHEN** a developer runs `gradlew` and JDK 25 is not yet installed locally
- **THEN** mise installs `temurin-25` before the build proceeds

#### Scenario: No mise.toml present in repository
- **WHEN** a developer inspects the repository root
- **THEN** `mise.toml` does NOT exist; `.java-version` is present with content `temurin-25`

## MODIFIED Requirements

### Requirement: AGENTS.md documents current JDK requirement
The `AGENTS.md` file SHALL accurately document JDK 25 as the required Java version and reference `.java-version` as the pinning mechanism, without prescribing a specific version manager tool.

#### Scenario: Developer reads AGENTS.md for environment setup
- **WHEN** a developer reads AGENTS.md to set up their environment
- **THEN** the documented JDK requirement matches the version in `.java-version` and does not mention `mise.toml`
