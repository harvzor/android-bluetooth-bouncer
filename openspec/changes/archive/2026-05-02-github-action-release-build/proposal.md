## Why

The project has a hermetic Docker-based build but no automated pipeline to produce and distribute release APKs. Pushing a version tag requires manual local builds and manual upload to GitHub Releases, which is error-prone and not reproducible for collaborators.

## What Changes

- Add a GitHub Actions workflow that triggers on version tag pushes (`v*`)
- The workflow builds a release APK using the existing `Dockerfile` with `BUILD_TYPE=release`
- The resulting APK is automatically uploaded as an asset to the corresponding GitHub Release

## Capabilities

### New Capabilities

- `github-action-release-build`: A CI/CD workflow that produces a release APK via the project's Dockerfile and publishes it to GitHub Releases when a version tag is pushed

### Modified Capabilities

<!-- No existing spec-level requirements are changing -->

## Impact

- **New file**: `.github/workflows/release-build.yml`
- **README.md**: Add section documenting how to trigger a release build
- **No source changes**: The Dockerfile, Gradle config, and app code are unaffected
- **GitHub token**: Workflow uses the built-in `GITHUB_TOKEN` secret for release creation — no extra secrets needed
- **Runner**: Requires an `ubuntu-latest` runner with Docker (standard on GitHub-hosted runners)
