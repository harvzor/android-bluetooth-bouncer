## 1. Gradle version configuration

- [x] 1.1 Replace hardcoded `versionCode = 1` and `versionName = "1.0"` in `app/build.gradle.kts` with logic that reads `appVersionName` Gradle property
- [x] 1.2 Implement semver-to-versionCode derivation (`major * 10_000 + minor * 100 + patch`) with fallback to `versionCode = 1` / `versionName = "0.0.0-dev"` when property is absent or malformed

## 2. Dockerfile update

- [x] 2.1 Add `ARG VERSION` declaration to the Dockerfile (after the existing `ARG BUILD_TYPE`)
- [x] 2.2 Update the `gradlew assemble` command to conditionally include `-PappVersionName=${VERSION}` when `VERSION` is non-empty

## 3. GitHub Actions workflow update

- [x] 3.1 Add a step to `release-build.yml` that extracts the semver string from `GITHUB_REF_NAME` (strips leading `v`) and writes it to `$GITHUB_ENV` or a step output
- [x] 3.2 Update the `docker build` invocation to include `--build-arg VERSION=<extracted-version>`

## 4. Verification

- [x] 4.1 Run a local Docker build without `VERSION` arg and confirm the APK `versionName` is `"0.0.0-dev"`
- [x] 4.2 Run a local Docker build with `--build-arg VERSION=1.2.3` and confirm the APK `versionName` is `"1.2.3"` and `versionCode` is `10203`
