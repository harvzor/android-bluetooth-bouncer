## Requirements

### Requirement: Release APK built automatically on version tag push
The system SHALL trigger a GitHub Actions workflow when a tag matching `v*` is pushed to the repository. The workflow SHALL extract the semver string from the tag (stripping the leading `v`), build a signed release APK using the project's `Dockerfile` with `BUILD_TYPE=release` and the extracted version passed as the `VERSION` build argument, and SHALL upload the resulting APK as an asset to the corresponding GitHub Release, creating the release if it does not already exist.

#### Scenario: Pushing a version tag triggers the workflow
- **WHEN** a developer pushes a tag matching `v*` (e.g., `v1.2.3`) to the repository
- **THEN** the GitHub Actions workflow `release-build.yml` is triggered automatically

#### Scenario: Release APK is produced via Dockerfile with version injected
- **WHEN** the workflow runs for tag `v1.2.3`
- **THEN** it executes `docker build` with `--build-arg BUILD_TYPE=release`, `--build-arg VERSION=1.2.3`, signing secrets, and `--output type=local,dest=out .` from the repository root
- **THEN** a signed APK named `bluetooth-bouncer-1.2.3.apk` is written to the `out/` directory on the runner

#### Scenario: APK is uploaded to the GitHub Release
- **WHEN** the Docker build completes successfully
- **THEN** a GitHub Release is created for the pushed tag (or the existing release is updated)
- **THEN** the APK file from `out/` is attached as a release asset

### Requirement: Release APK signed using repository secrets
The workflow SHALL pass signing credentials to the Docker build via BuildKit secret mounts. Four repository-level secrets SHALL be configured: `RELEASE_KEYSTORE_BASE64` (base64-encoded keystore file), `RELEASE_KEYSTORE_PASSWORD` (store password), `RELEASE_KEY_ALIAS` (key alias), and `RELEASE_KEY_PASSWORD` (key password). The keystore SHALL be decoded from base64 before the Docker build step and mounted as a BuildKit secret. Passwords SHALL be passed via `--secret id=...,env=...` using step-level environment variables sourced from repository secrets — not as build arguments.

#### Scenario: Signing secrets passed securely to Docker build
- **WHEN** the workflow runs
- **THEN** the keystore is decoded from `RELEASE_KEYSTORE_BASE64` to a temporary file on the runner
- **THEN** `docker build` is invoked with `--secret id=keystore,src=./release.keystore` and password secrets via `--secret id=...,env=...`
- **THEN** no signing credentials appear as `--build-arg` values

#### Scenario: Four repository secrets must be configured
- **WHEN** a repository maintainer sets up the workflow for the first time
- **THEN** they must configure `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` as repository-level secrets

### Requirement: Workflow uses Ubuntu runner with Docker
The workflow SHALL run on `ubuntu-latest` GitHub-hosted runners, which SHALL have Docker with BuildKit support available.

#### Scenario: BuildKit output mode is used for APK extraction
- **WHEN** the workflow invokes `docker build`
- **THEN** the `--output type=local,dest=out` flag is used so the APK is extracted directly to the runner filesystem without starting a container
