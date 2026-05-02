## Context

The release workflow builds APKs on every `v*` tag push. The APK's internal `versionName` and `versionCode` (declared in `app/build.gradle.kts`) are hardcoded to `"1.0"` and `1`, so every published APK reports the same version regardless of which tag triggered the build. The build chain is: GHA → `docker build` → Gradle inside the container.

## Goals / Non-Goals

**Goals:**
- `versionName` in the built APK matches the tag's semver string (e.g. `v1.2.3` → `"1.2.3"`)
- `versionCode` is derived deterministically from the semver (no manual bumping)
- Local builds still work without any environment setup, using a clear dev fallback

**Non-Goals:**
- Play Store publishing or versionCode constraints beyond GitHub Releases
- Embedding git commit hash or build metadata in the version string
- Changing the tag naming convention (`v*` stays as-is)

## Decisions

### Thread the version through as a build-time Gradle property

The semver string travels as a value through three layers:

```
GHA env var        Docker build arg     Gradle property
GITHUB_REF_NAME ──▶ --build-arg       ──▶ -PappVersionName
(e.g. v1.2.3)       VERSION=1.2.3         read by build.gradle.kts
```

**Alternatives considered:**

- **`git describe` inside the container** — requires copying `.git/` into the Docker context (currently excluded by `.dockerignore`) and installing git in the build image. Adds complexity and breaks the hermetic build.
- **`version.properties` file committed to git** — requires updating the file on every release, either manually (error-prone) or via a CI commit (messy history).
- **Environment variable in GHA without Docker ARG** — can't reach inside the container without passing it as a build arg.

Build-time property injection is the minimal change: no new tooling, no new files, no git history noise.

### Derive versionCode from semver arithmetic

`versionCode = major * 10_000 + minor * 100 + patch`

Examples: `v1.2.3` → `10203`, `v0.1.0` → `100`, `v2.0.0` → `20000`.

**Constraint:** Works correctly as long as minor and patch components each remain below 100. This is a reasonable constraint for this project.

**Alternatives considered:**

- **`github.run_number`** — always increases but is unrelated to the version string and resets if the workflow is recreated.
- **Timestamp-based** — always increases but can overflow `Int.MAX_VALUE` on a long enough timeline and has no human-readable relationship to the release.

### Fallback for local / non-CI builds

When `appVersionName` is not provided, `build.gradle.kts` falls back to `versionName = "0.0.0-dev"` and `versionCode = 1`. This makes dev builds clearly distinguishable from releases without requiring developers to set any environment variables.

## Risks / Trade-offs

- **semver components ≥ 100** → `versionCode` derivation breaks (e.g. `v1.100.0` collides with `v1.1.0`). Mitigation: document the constraint; the project is unlikely to need patch or minor values ≥ 100 in the near term.
- **Malformed tag** (e.g. `v1.2` or `vbeta`) → Gradle property parsing will throw. Mitigation: the workflow only triggers on `v*` tags; add a parsing guard in `build.gradle.kts` that falls back gracefully if the string isn't valid semver.
- **Docker layer cache** — `app/build.gradle.kts` is copied in an early cache layer. Adding a `VERSION` ARG after it means the ARG value doesn't invalidate the dependency-resolution cache layer (it's only consumed in the final `gradlew assemble` command). No cache regression.
