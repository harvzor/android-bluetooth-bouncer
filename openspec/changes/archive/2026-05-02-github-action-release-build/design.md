## Context

The project has a fully hermetic Docker-based build (see `docker-build` spec) that produces an APK using `eclipse-temurin:25-jdk-noble` with the Android 36 SDK. The Dockerfile already accepts a `BUILD_TYPE` build arg (default: `debug`) and is designed for `docker build --output=out .` extraction. No GitHub Actions workflow exists today, so every release requires a manual local build and manual asset upload to GitHub Releases.

## Goals / Non-Goals

**Goals:**
- Automate release APK production on every version tag push (`v*`)
- Upload the resulting APK as an asset to the corresponding GitHub Release
- Reuse the existing Dockerfile unchanged — no divergence between local and CI builds
- Use only the built-in `GITHUB_TOKEN`; no additional secrets required

**Non-Goals:**
- APK signing (no signing config or keystore is in the project; unsigned release APK is the intended output)
- Pull request or push-to-main CI builds (out of scope for this change)
- Multi-architecture Docker builds
- Play Store / Firebase App Distribution upload

## Decisions

### Use Docker BuildKit `--output` to extract the APK

The Dockerfile's `scratch`-based export stage is designed for `docker build --output type=local,dest=out .`. This avoids running a container and then `docker cp`-ing out of it — the APK lands directly on the runner filesystem. GitHub-hosted `ubuntu-latest` runners have BuildKit enabled by default.

**Alternative considered**: Run `docker run` + `docker cp` to extract the APK. Rejected because it leaves stopped containers behind and is more complex than the purpose-built export stage.

### Use `softprops/action-gh-release` for release creation and asset upload

This action handles creating the GitHub Release from the tag (if it doesn't exist) and uploading assets in a single step. It is widely used, well-maintained, and far less boilerplate than shelling out to `gh release create` + `gh release upload` separately.

**Alternative considered**: `gh` CLI calls directly in the workflow. Rejected because it requires separate steps for release creation vs. asset upload and brittle shell scripting to detect if the release already exists.

### Trigger only on `v*` tags

Triggering on `push: tags: ['v*']` ensures the workflow only runs for intentional version releases (e.g., `v1.0.0`, `v1.2.3-rc1`). Branch pushes and PRs are unaffected.

**Alternative considered**: `release: types: [created]`. Rejected because it requires manually creating the GitHub Release in the UI before the workflow can run, which defeats the goal of full automation from a single `git push --tags`.

## Risks / Trade-offs

- **BuildKit cache miss on cold runners**: Each GitHub Actions run starts fresh; Docker layer caching is not persisted across runs by default. The Dockerfile's layer ordering (SDK install → deps → source) minimises re-work per run, but cold builds still download the Android SDK and Maven dependencies every time. → Mitigation: Accept as-is for now; Docker layer cache can be added later via `actions/cache` with `mode=max` if build times become a problem.
- **Unsigned APK**: The release APK is unsigned. It cannot be installed on a device without explicit APK signing or Play Store distribution. → Mitigation: Document clearly in README. Signing is a separate future concern.
- **Single-job, no parallelism**: The workflow is one job. If build times grow, it blocks the release. → Mitigation: Acceptable for current project size; can be split later.

## Migration Plan

1. Merge the workflow file into the default branch
2. Push a test tag (e.g., `v0.0.1-test`) to verify the full pipeline end-to-end
3. Delete the test release/tag after verification

No rollback needed — removing the workflow file disables the automation entirely with no side effects.

## Open Questions

None — all decisions are resolved for the initial implementation.
