### Requirement: APK versionName derived from build property
The system SHALL read the Gradle property `appVersionName` in `app/build.gradle.kts` and use its value as the APK `versionName`. If the property is absent or blank, `versionName` SHALL default to `"0.0.0-dev"`.

#### Scenario: Version property present and valid
- **WHEN** `./gradlew assembleRelease -PappVersionName=1.2.3` is invoked
- **THEN** the built APK's `versionName` is `"1.2.3"`

#### Scenario: Version property absent (local build)
- **WHEN** `./gradlew assembleDebug` is invoked without `-PappVersionName`
- **THEN** the built APK's `versionName` is `"0.0.0-dev"`

### Requirement: APK versionCode derived from semver arithmetic
The system SHALL derive `versionCode` from the numeric parts of `appVersionName` using the formula `major * 10_000 + minor * 100 + patch`. Any pre-release suffix (e.g. `-rc1`, `-alpha`) SHALL be stripped before parsing. If the property is absent or the numeric parts cannot be parsed, `versionCode` SHALL default to `1`.

#### Scenario: versionCode derived from valid semver
- **WHEN** `appVersionName` is `"1.2.3"`
- **THEN** `versionCode` is `10203`

#### Scenario: versionCode derived from zero-minor version
- **WHEN** `appVersionName` is `"2.0.0"`
- **THEN** `versionCode` is `20000`

#### Scenario: versionCode derived from version with pre-release suffix
- **WHEN** `appVersionName` is `"1.0.0-rc1"`
- **THEN** `versionName` is `"1.0.0-rc1"`
- **THEN** `versionCode` is `10000` (suffix stripped before arithmetic)

#### Scenario: versionCode fallback when property absent
- **WHEN** `appVersionName` is not provided
- **THEN** `versionCode` is `1`

#### Scenario: versionCode fallback for malformed version string
- **WHEN** `appVersionName` is a string that is not valid three-part semver (e.g. `"beta"`)
- **THEN** `versionCode` falls back to `1` and `versionName` falls back to `"0.0.0-dev"` without crashing the build
