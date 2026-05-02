## 1. Workflow File

- [x] 1.1 Create `.github/workflows/` directory
- [x] 1.2 Create `.github/workflows/release-build.yml` with trigger `on: push: tags: ['v*']`
- [x] 1.3 Add `permissions: contents: write` so `GITHUB_TOKEN` can create releases and upload assets
- [x] 1.4 Add `actions/checkout@v4` step to check out the repository
- [x] 1.5 Add `docker build --build-arg BUILD_TYPE=release --output type=local,dest=out .` step
- [x] 1.6 Add `softprops/action-gh-release` step to create the GitHub Release and upload `out/*.apk`

## 2. Validation

- [x] 2.1 Push a test tag (e.g., `v0.0.1-test`) and confirm the workflow triggers and completes successfully
- [x] 2.2 Verify the APK appears as a release asset on the GitHub Release page
- [x] 2.3 Delete the test tag and release after verification

## 3. Documentation

- [x] 3.1 Update `README.md` to document the release process (push a `v*` tag to trigger an automated release build)
