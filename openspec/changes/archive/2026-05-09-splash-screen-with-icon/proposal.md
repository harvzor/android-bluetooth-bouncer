## Why

The app currently shows the system default splash screen (white background + app icon with no explicit configuration), which creates a jarring flash in dark mode because the underlying Activity theme is hardcoded to `Theme.Material.Light`. Adding a properly configured splash screen with light/dark support gives the app a polished first impression and eliminates the visual mismatch between the splash and the Compose UI.

## What Changes

- Add `androidx.core:core-splashscreen` dependency (`1.2.0`)
- Create a dedicated splash screen theme that displays the existing launcher foreground icon (`ic_launcher_foreground`) centred on a system-appropriate background
- Add a `values-night/themes.xml` override so the splash background matches the dark UI (`#1C1B1F`) instead of always showing white
- Fix the base app XML theme to use a `DayNight` parent so the window background no longer flickers light before Compose renders in dark mode
- Call `installSplashScreen()` in `MainActivity` for a smooth, flicker-free transition from splash into the Compose UI
- Remove the `SideEffect` in `Theme.kt` that overrides the status bar colour with `colorScheme.primary`, which conflicts with `enableEdgeToEdge()`

## Capabilities

### New Capabilities

- `splash-screen`: Branded splash screen displayed on app launch using the existing launcher icon, adapting background colour to the system light/dark theme setting

### Modified Capabilities

*(none — no existing spec-level capabilities are changing)*

## Impact

- **Dependencies**: `gradle/libs.versions.toml`, `app/build.gradle.kts` — new `core-splashscreen` library
- **Resources**: `app/src/main/res/values/themes.xml` (modified), `app/src/main/res/values-night/themes.xml` (new)
- **Manifest**: `AndroidManifest.xml` — `MainActivity` theme changed to splash theme
- **Kotlin**: `MainActivity.kt` — `installSplashScreen()` call added; `ui/theme/Theme.kt` — `SideEffect` status bar override removed with associated unused imports
- **No breaking changes** — purely additive/visual; no API surface, data model, or behaviour changes
