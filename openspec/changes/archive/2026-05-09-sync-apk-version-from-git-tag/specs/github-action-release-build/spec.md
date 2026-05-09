## MODIFIED Requirements

### Requirement: Release APK built automatically on version tag push
The system SHALL trigger a GitHub Actions workflow when a tag matching `v*` is pushed to the repository. The workflow SHALL extract the semver string from the tag (stripping the leading `v`), build a release APK using the project's `Dockerfile` with `BUILD_TYPE=release` and the extracted version passed as the `VERSION` build argument, and SHALL upload the resulting APK as an asset to the corresponding GitHub Release, creating the release if it does not already exist.

#### Scenario: Pushing a version tag triggers the workflow
- **WHEN** a developer pushes a tag matching `v*` (e.g., `v1.2.3`) to the repository
- **THEN** the GitHub Actions workflow `release-build.yml` is triggered automatically

#### Scenario: Release APK is produced via Dockerfile with version injected
- **WHEN** the workflow runs for tag `v1.2.3`
- **THEN** it executes `docker build --build-arg BUILD_TYPE=release --build-arg VERSION=1.2.3 --output type=local,dest=out .` from the repository root
- **THEN** an APK file is written to the `out/` directory on the runner

#### Scenario: APK is uploaded to the GitHub Release
- **WHEN** the Docker build completes successfully
- **THEN** a GitHub Release is created for the pushed tag (or the existing release is updated)
- **THEN** the APK file from `out/` is attached as a release asset

#### Scenario: No additional secrets required
- **WHEN** the workflow authenticates with GitHub for release creation and asset upload
- **THEN** it uses only the built-in `GITHUB_TOKEN` secret — no repository-level secrets need to be configured
