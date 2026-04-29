## 1. Project Scaffolding

- [ ] 1.1 Create Android project with Gradle Kotlin DSL (package: `net.harveywilliams.bluetoothbouncer`, min SDK 31, target SDK 35)
- [ ] 1.2 Add dependencies: Jetpack Compose + Material 3, Room, Navigation Compose, Shizuku API, AndroidX lifecycle
- [ ] 1.3 Configure AndroidManifest with permissions (`BLUETOOTH_CONNECT`, `RECEIVE_BOOT_COMPLETED`), Shizuku provider, and activity declaration

## 2. Data Layer

- [ ] 2.1 Create `BlockedDeviceEntity` Room entity (MAC address, device name, timestamp)
- [ ] 2.2 Create `BlockedDeviceDao` with insert, delete, query-all, and query-by-MAC operations (return Flow for reactive UI)
- [ ] 2.3 Create `AppDatabase` Room database class

## 3. Shizuku Bridge

- [ ] 3.1 Define `IBluetoothBouncerUserService.aidl` interface with `setConnectionPolicy(macAddress: String, policy: int): int[]` and `isAlive(): boolean`
- [ ] 3.2 Implement `BluetoothBouncerUserService` — runs as shell UID, obtains A2DP/Headset/HID profile proxies, calls `setConnectionPolicy` on each
- [ ] 3.3 Implement `ShizukuHelper` — detect Shizuku state (not installed / not running / ready), request permission, bind/unbind UserService

## 4. UI — Shizuku Setup Screen

- [ ] 4.1 Create `ShizukuSetupScreen` composable — shows Shizuku status, install link, and setup instructions for ADB/Wireless Debugging
- [ ] 4.2 Implement live Shizuku status detection (updates when Shizuku starts/stops while on the setup screen)

## 5. UI — Device List Screen

- [ ] 5.1 Create `DeviceListScreen` composable — lists paired Bluetooth devices with block/allow toggle switches
- [ ] 5.2 Show connection status indicator for each device (connected vs disconnected)
- [ ] 5.3 Show Shizuku status bar at the top of the device list
- [ ] 5.4 Handle empty states: no paired devices, Bluetooth disabled
- [ ] 5.5 Handle toggle errors: Shizuku unavailable, revert toggle on failure

## 6. ViewModel and Business Logic

- [ ] 6.1 Create `DeviceListViewModel` — load paired devices, merge with Room blocked-device state, expose combined UI state as Flow
- [ ] 6.2 Implement block/unblock actions — call ShizukuHelper to set policy, update Room on success, revert UI on failure
- [ ] 6.3 Request `BLUETOOTH_CONNECT` runtime permission with rationale dialog

## 7. Navigation and App Shell

- [ ] 7.1 Create `MainActivity` with single-Activity Compose setup
- [ ] 7.2 Set up Navigation Compose graph: Shizuku setup screen (conditional) → device list screen
- [ ] 7.3 Create `BluetoothBouncerApp` Application class with Room database singleton
- [ ] 7.4 Apply Material 3 theme

## 8. Background Receivers

- [ ] 8.1 Create `BootReceiver` — on `BOOT_COMPLETED`, re-apply FORBIDDEN policies for all blocked devices (if Shizuku is running)
- [ ] 8.2 Create `BondStateReceiver` — on `BOND_STATE_CHANGED` to `BONDED`, check blocked list and re-apply policy if device was previously blocked

## 9. Verification

- [ ] 9.1 Build the project and verify it compiles without errors
- [ ] 9.2 Manual test plan: install on device, verify Shizuku setup flow, block/unblock a device, verify policy persists across app restart
