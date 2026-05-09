## Context

The app is a Compose-first single-activity Android app (`minSdk 31`, `targetSdk 36`). Android 12+ mandates a system splash screen for every app — without explicit configuration the system shows a default white screen with the adaptive icon. The app's XML theme is currently `Theme.Material.Light.NoActionBar` (hardcoded light), while the Compose layer correctly uses `isSystemInDarkTheme()` + dynamic colors. This mismatch produces a white flash on launch when the device is in dark mode.

The existing launcher foreground asset (`ic_launcher_foreground.webp`) is already well-suited for the splash: the bouncer character is centred with generous transparent padding, making it render cleanly inside the circular splash icon mask.

## Goals / Non-Goals

**Goals:**
- Show a properly branded splash screen (existing launcher foreground icon) on every app launch
- Splash background matches the system light/dark theme preference (no white flash in dark mode)
- Smooth, flicker-free transition from splash into Compose content
- Fix the underlying XML theme so the window background is also dark-mode-aware

**Non-Goals:**
- Custom splash animations or AnimatedVectorDrawable
- Artificially extending splash duration (no fake loading delays)
- Branding image at the bottom of the splash
- Separate splash activity

## Decisions

### 1. Use `androidx.core:core-splashscreen` 1.2.0

**Decision**: Add the AndroidX compat library rather than using platform APIs directly.

**Rationale**: Although `minSdk 31` gives us the platform `android.window.SplashScreen` API natively, `installSplashScreen()` from the compat library provides a higher-level `SplashScreen` handle (for future animation/keep-on-screen use), is the officially recommended approach, and costs ~20 KB — negligible.

**Alternative considered**: Using platform APIs directly (no dependency). Rejected because `installSplashScreen()` handles the `postSplashScreenTheme` switch automatically and gives a cleaner entry point for future `setOnExitAnimationListener` if animations are ever desired.

### 2. Light/dark via `values-night/` resource qualifier, not a `DayNight` XML theme parent

**Decision**: Keep `Theme.Material.Light.NoActionBar` as the base for light, add `Theme.Material.NoActionBar` in `values-night/`, rather than switching the parent to `Theme.Material.DayNight.NoActionBar`.

**Rationale**: The XML theme is a thin shell — Compose owns all actual UI. We only need the window background to be dark-mode-aware. The `values-night/` override is the idiomatic, minimal way to achieve this without introducing an AppCompat dependency (`Theme.AppCompat.DayNight`) or relying on `Theme.Material.DayNight` which was only guaranteed from API 29 onwards. Since Compose's `BluetoothBouncerTheme` drives all colors at runtime, the XML theme just needs to set the right window background to prevent flicker.

**Alternative considered**: `Theme.Material.DayNight.NoActionBar` (single file, no `values-night/`). Rejected because it requires API 29 minimum guarantee that the `DayNight` variant exists in the platform — using the qualifier is more explicit and requires zero additional dependencies.

### 3. Dark splash background colour `#1C1B1F`

**Decision**: Use `#1C1B1F` (Material 3 default dark surface) as the dark mode splash background.

**Rationale**: The app uses Material 3 dynamic colour at runtime. `#1C1B1F` is the M3 default dark `surface` colour, so when dynamic colours are not available or on first launch, the splash background matches what the Compose UI will render — minimising perceived flash.

### 4. Remove `SideEffect` status bar colour override in `Theme.kt`

**Decision**: Delete the `SideEffect` block that sets `window.statusBarColor = colorScheme.primary.toArgb()`.

**Rationale**: `enableEdgeToEdge()` already handles status bar styling (transparent bars, correct icon contrast per theme). The `SideEffect` override conflicts with it, causing an opaque primary-coloured status bar instead of the intended transparent edge-to-edge treatment. Its removal is a bug fix bundled here because it directly relates to the dark-mode window theming work.

## Risks / Trade-offs

- **OEM splash customisation**: Some OEMs (Samsung, MIUI) apply their own splash screen overlays or colour transformations. The circular icon mask size and animation behaviour may differ slightly across devices. The existing foreground asset is well-padded and should be safe. → Mitigation: test on the physical test device (`192.168.178.21:42059`).

- **Dynamic colour + splash mismatch**: The splash background is a static colour (`#FFFFFF` / `#1C1B1F`), but the actual app background is driven by wallpaper dynamic colour at runtime. These will rarely match exactly. → Accepted trade-off: an exact match is impossible without reading dynamic colours before Compose initialises. The M3 defaults are close enough.

- **`installSplashScreen()` call order**: Must be called before `super.onCreate()`. If this order is wrong the call is a no-op and the splash theme won't transition. → Mitigation: covered in tasks, easy to verify by running the app.

## Open Questions

*(none — all decisions resolved during exploration)*
