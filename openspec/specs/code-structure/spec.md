# Capability: Code Structure

## Requirements

### Requirement: Bluetooth profile operations use a single dispatch path
The `BluetoothBouncerUserService` SHALL implement `setConnectionPolicy`, `connectDevice`, and `disconnectDevice` through a shared `forEachProfile` helper rather than duplicated proxy-dispatch loops, so that any change to proxy timeout handling, error logging, or proxy enumeration is made in exactly one place.

#### Scenario: Profile operation error handling is consistent
- **WHEN** a Bluetooth proxy future times out during any of the three AIDL operations
- **THEN** the timeout is handled identically (log warning, return `-1` for that profile slot) regardless of which operation triggered it

---

### Requirement: Shizuku IPC calls use a single result-checking path
The `ShizukuHelper` SHALL route `setConnectionPolicy`, `connectDevice`, and `disconnectDevice` through a shared `callService` helper, so that the `awaitServiceIfNeeded()` guard, `userService` null check, and `results.none { it == 1 }` failure check are applied uniformly to all three operations.

#### Scenario: Unavailable UserService produces consistent failure
- **WHEN** `userService` is null and Shizuku is not available
- **THEN** all three helper methods (`setConnectionPolicy`, `connectDevice`, `disconnectDevice`) return `Result.failure(IllegalStateException("Shizuku UserService is not available"))` with identical messaging

---

### Requirement: ACL connection detection uses a single reflection site
The app SHALL use a single `BluetoothAclHelper` utility for the hidden `BluetoothDevice.isConnected()` reflection call, so that any change to the reflection approach is made in one place and both `DeviceListViewModel` and `DeviceWatcherService` benefit automatically.

#### Scenario: ACL check is consistent across callers
- **WHEN** both the ViewModel and the WatcherService need to determine whether a device has an active ACL link
- **THEN** both use `BluetoothAclHelper` and thus apply identical reflection logic and error handling

---

### Requirement: Device section classification uses a single computation path
The `DeviceListViewModel` SHALL compute `DeviceSection` via a single `computeSection` function, used by both the initial device list build and the decay ticker remap, so that the classification rules cannot diverge between the two call sites.

#### Scenario: Section classification is identical during decay
- **WHEN** the decay ticker remaps a device whose ACL connection just dropped
- **THEN** the `section` assigned is identical to what `refreshDeviceList` would assign for the same device state
