## ADDED Requirements

### Requirement: Connect a Bluetooth device via UserService
The Shizuku UserService SHALL expose a `connectDevice(macAddress: String): IntArray` method on its AIDL interface. The UserService SHALL obtain profile proxies for A2DP, Headset, and HID Host and call the hidden `connect(device)` method on each via reflection. The return value SHALL follow the existing convention: one entry per profile — `1` = success, `0` = returned false, `-1` = proxy unavailable. The app SHALL treat the call as a failure if no profile entry equals `1`.

#### Scenario: Connect succeeds on at least one profile
- **WHEN** the app calls `connectDevice(macAddress)` via the UserService
- **AND** at least one profile proxy's `connect(device)` returns true
- **THEN** the UserService returns an IntArray with at least one entry equal to `1`
- **THEN** the app treats the call as a success

#### Scenario: Connect fails on all profiles
- **WHEN** the app calls `connectDevice(macAddress)` via the UserService
- **AND** all profile proxies return false or are unavailable
- **THEN** the UserService returns an IntArray with no entry equal to `1`
- **THEN** the app treats the call as a failure and shows an error snackbar

#### Scenario: Profile proxy unavailable during connect
- **WHEN** the UserService cannot obtain a profile proxy for a given profile
- **THEN** the UserService records `-1` for that profile and continues with the remaining profiles

### Requirement: Disconnect a Bluetooth device via UserService
The Shizuku UserService SHALL expose a `disconnectDevice(macAddress: String): IntArray` method on its AIDL interface. The UserService SHALL obtain profile proxies for A2DP, Headset, and HID Host and call the hidden `disconnect(device)` method on each via reflection. The return value SHALL follow the same convention as `connectDevice`. The app SHALL treat the call as a failure if no profile entry equals `1`.

#### Scenario: Disconnect succeeds on at least one profile
- **WHEN** the app calls `disconnectDevice(macAddress)` via the UserService
- **AND** at least one profile proxy's `disconnect(device)` returns true
- **THEN** the UserService returns an IntArray with at least one entry equal to `1`
- **THEN** the app treats the call as a success

#### Scenario: Disconnect fails on all profiles
- **WHEN** the app calls `disconnectDevice(macAddress)` via the UserService
- **AND** all profile proxies return false or are unavailable
- **THEN** the UserService returns an IntArray with no entry equal to `1`
- **THEN** the app treats the call as a failure and shows an error snackbar

#### Scenario: Profile proxy unavailable during disconnect
- **WHEN** the UserService cannot obtain a profile proxy for a given profile
- **THEN** the UserService records `-1` for that profile and continues with the remaining profiles
