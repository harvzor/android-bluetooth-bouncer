## Why

Android automatically reconnects paired Bluetooth devices (headphones, speakers, car systems) whenever they're in range, with no per-device control. Users who pair multiple devices often experience unwanted auto-connections — e.g., a work headset connecting during personal time, or a car stereo hijacking audio. The OS provides `setConnectionPolicy(CONNECTION_POLICY_FORBIDDEN)` to prevent this, but it requires `BLUETOOTH_PRIVILEGED`, which is inaccessible to regular apps. By using Shizuku as a privilege bridge, a lightweight app can expose this OS-level policy toggle to end users.

## What Changes

- New Android app ("Bluetooth Bouncer") built from scratch with Kotlin + Jetpack Compose
- Lists all paired Bluetooth devices with block/allow toggle switches
- Integrates with Shizuku to call `setConnectionPolicy(FORBIDDEN)` on blocked devices across A2DP, HFP, and HID profiles
- Persists the user's blocked-device list in a local Room database
- Guides users through Shizuku setup when not yet configured
- Re-applies FORBIDDEN policies on boot for devices re-paired while the app wasn't active

## Capabilities

### New Capabilities
- `device-blocking`: Core capability — listing paired Bluetooth devices, toggling block/allow state, persisting preferences, and enforcing connection policies via `setConnectionPolicy`
- `shizuku-bridge`: Shizuku integration — permission management, UserService lifecycle, and executing privileged Bluetooth API calls as the shell UID

### Modified Capabilities
<!-- No existing capabilities — this is a greenfield project -->

## Impact

- **Dependencies**: Shizuku API library, Room, Jetpack Compose + Material 3, AndroidX lifecycle
- **Permissions**: `BLUETOOTH_CONNECT`, `RECEIVE_BOOT_COMPLETED`
- **Min SDK**: 31 (Android 12) — required for `BLUETOOTH_CONNECT` runtime permission model
- **Target SDK**: 35
- **External requirement**: User must install Shizuku app and start it via ADB or Wireless Debugging
- **Package**: `net.harveywilliams.bluetoothbouncer`
