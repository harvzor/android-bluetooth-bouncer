## Context

The device list screen (`DeviceListScreen.kt`) renders each paired Bluetooth device with an icon, status text, and toggles. Currently, connection state is communicated through a combination of icon tint (`MaterialTheme.colorScheme.primary`), a green `Badge` dot overlay on the icon, and green status text. Detection state uses `onSurfaceVariant` — a theme-derived gray.

Both colors are wallpaper-dependent on Android 12+ via Material3 dynamic color. This means the exact colors shift with the user's wallpaper, making the UI unpredictable.

The specific problems:
- The green `Badge` dot is redundant — the icon tint already signals connected state
- "Connected" being green has no semantic grounding; Bluetooth's own brand color is blue
- "Detected" being a dynamic gray has no warmth or distinction from default inactive text
- All state colors vary across devices and wallpapers

## Goals / Non-Goals

**Goals:**
- Replace the green Badge dot with no visual decoration (icon tint alone is sufficient)
- Use the Bluetooth SIG brand color (`#0082FC`) for connected state: icon tint + status text
- Use a fixed warm salmon-orange (`#E8A06C`) for detected state text
- Make all state colors hardcoded constants, independent of dynamic theming

**Non-Goals:**
- Changing any connection, detection, or blocking logic
- Introducing a formal color constants file or theme tokens
- Changing the "Shizuku: Ready" banner, toggle colors, or any other UI element
- Supporting light theme variations (app is dark-theme only in practice)

## Decisions

**Decision 1: Hardcode colors rather than extend the theme**

The app has a minimal theme (`Theme.kt`) with no custom color definitions — it relies entirely on Material3 defaults and dynamic color. Introducing new semantic tokens (e.g., `colorScheme.bluetoothConnected`) would require a custom `ColorScheme` wrapper for just two values.

For a two-color change scoped to one file, inline constants are simpler and more transparent. The constants will be defined as local `val`s at the top of the `DeviceRow` composable or as file-level constants.

Alternatives considered:
- **Theme tokens**: Overkill for two colors in one composable; would require custom `MaterialTheme` extensions
- **Resource colors (`colors.xml`)**: Mixing View-system resources with Compose is discouraged; adds unnecessary indirection

**Decision 2: Use `Color(0xFF0082FC)` for Bluetooth Blue**

`#0082FC` is the Bluetooth SIG's official brand blue. It is recognizable, accessible on dark backgrounds (contrast ratio ~4.8:1 on near-black), and semantically appropriate.

**Decision 3: Use `Color(0xFFE8A06C)` for Detected**

A warm salmon-orange that complements the dark background and is visually distinct from both the blue connected state and the white device name text. It suggests "nearby but not connected" — warm and present, but not authoritative.

**Decision 4: Remove Badge dot entirely**

The Badge adds visual noise without adding information. The icon tint already differentiates connected vs. not-connected. Removing it simplifies the icon rendering from a `Box` with two children to a single `Icon`.

## Risks / Trade-offs

- **Color accessibility**: `#0082FC` on the app's dark background (`#1F2515`-ish) has adequate contrast, but hasn't been formally audited with WCAG tools. → Low risk for a status label; exact background varies.
- **Hardcoded colors ignore system dark/light switching**: The app is dark-theme only in practice and the existing state colors were already hardcoded greens/oranges, so this is consistent with the existing approach.
- **`#E8A06C` may drift from `primary`**: If the user's dynamic primary was salmon, this was previously in sync. Now it's fixed. This is intentional — stability over theme harmony.
