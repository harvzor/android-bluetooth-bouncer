## 1. Remove Badge Dot

- [x] 1.1 Delete the `if (device.isConnected) { Badge(...) }` block (lines 349-354 of `DeviceListScreen.kt`)
- [x] 1.2 Verify the `Box` wrapping the icon can be simplified to a plain `Icon` (no more overlay needed)

## 2. Update Icon Tint

- [x] 2.1 Replace `MaterialTheme.colorScheme.primary` with `Color(0xFF0082FC)` as the connected icon tint (line 344)
- [x] 2.2 Ensure the `Color` import is present (should already be imported)

## 3. Update Status Text Colors

- [x] 3.1 Change "Connected" text color from `Color(0xFF4CAF50)` to `Color(0xFF0082FC)` (line 380)
- [x] 3.2 Change "Temporarily connected" text color from `Color(0xFFFF9800)` to `Color(0xFF0082FC)` (line 374)
- [x] 3.3 Change "Detected" text color from `MaterialTheme.colorScheme.onSurfaceVariant` to `Color(0xFFE8A06C)` (line 386)
- [x] 3.4 Change "Detected Xs ago" text color from `MaterialTheme.colorScheme.onSurfaceVariant` to `Color(0xFFE8A06C)` (line 392)

## 4. Build & Verify

- [x] 4.1 Build the app (`gradlew assembleDebug`)
- [x] 4.2 Install on physical device and visually confirm: connected device shows blue icon + blue "Connected" text, no dot
- [x] 4.3 Confirm detected-only devices show salmon-orange "Detected" or "Detected Xs ago" text
