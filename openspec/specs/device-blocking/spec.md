### Requirement: List paired Bluetooth devices
The app SHALL display all currently paired Bluetooth devices on the main screen. Each device entry SHALL show the device name and MAC address. The list SHALL refresh when the user returns to the app or when Bluetooth bond state changes.

#### Scenario: Paired devices are displayed
- **WHEN** the user opens the app and Bluetooth is enabled with paired devices
- **THEN** the app displays a list of all paired Bluetooth devices with their names and MAC addresses

#### Scenario: No paired devices
- **WHEN** the user opens the app and there are no paired Bluetooth devices
- **THEN** the app displays an empty state message indicating no paired devices were found

#### Scenario: Bluetooth is disabled
- **WHEN** the user opens the app and Bluetooth is disabled
- **THEN** the app displays a message prompting the user to enable Bluetooth

### Requirement: Block a device from auto-connecting
The app SHALL provide a toggle for each paired device to block it from auto-connecting. When a device is toggled to "blocked", the app SHALL set `CONNECTION_POLICY_FORBIDDEN` on all supported Bluetooth profiles (A2DP, Headset, HID Host) for that device via the Shizuku bridge. The device SHALL remain paired but unable to initiate or accept auto-connections.

#### Scenario: User blocks a device
- **WHEN** the user toggles a paired device to "blocked"
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on A2DP, Headset, and HID Host profiles
- **THEN** the device's blocked state is persisted in the local database
- **THEN** the toggle visually reflects the blocked state

#### Scenario: Blocking fails due to Shizuku unavailable
- **WHEN** the user toggles a device to "blocked" but Shizuku is not running
- **THEN** the app displays an error message indicating Shizuku is required
- **THEN** the toggle reverts to its previous state

### Requirement: Unblock a device to allow auto-connecting
The app SHALL allow the user to unblock a previously blocked device. When unblocked, the app SHALL set `CONNECTION_POLICY_ALLOWED` on all supported Bluetooth profiles for that device. The device SHALL resume normal auto-connection behavior.

#### Scenario: User unblocks a device
- **WHEN** the user toggles a blocked device to "allowed"
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on A2DP, Headset, and HID Host profiles
- **THEN** the device's record is updated in the local database
- **THEN** the toggle visually reflects the allowed state

### Requirement: Persist blocked device list
The app SHALL persist the list of blocked devices in a local Room database. The blocked state SHALL survive app restarts and device reboots. Each record SHALL store at minimum the device MAC address and device name at the time of blocking.

#### Scenario: Blocked devices survive app restart
- **WHEN** the user has blocked devices and restarts the app
- **THEN** the device list shows the correct blocked/allowed state for all paired devices based on persisted data

#### Scenario: Paired device no longer exists
- **WHEN** a device in the blocked list has been unpaired from the phone
- **THEN** the device is not shown in the main list (only currently paired devices are shown)

### Requirement: Show device connection status
The app SHALL indicate whether each paired device is currently connected. This helps users understand which devices are actively connected before deciding to block them.

#### Scenario: Connected device is indicated
- **WHEN** a paired device is currently connected via any Bluetooth profile
- **THEN** the device entry displays a "Connected" indicator

#### Scenario: Disconnected device is indicated
- **WHEN** a paired device is paired but not currently connected
- **THEN** the device entry does not display a "Connected" indicator

### Requirement: Re-apply policies for re-paired devices
The app SHALL listen for `BOND_STATE_CHANGED` broadcasts. When a device that was previously in the blocked list is re-paired, the app SHALL automatically re-apply `CONNECTION_POLICY_FORBIDDEN` if Shizuku is available.

#### Scenario: Blocked device is re-paired
- **WHEN** a user unpairs and re-pairs a device that was previously blocked
- **AND** Shizuku is running
- **THEN** the app automatically re-applies `CONNECTION_POLICY_FORBIDDEN` on all supported profiles

#### Scenario: Blocked device is re-paired without Shizuku
- **WHEN** a user unpairs and re-pairs a device that was previously blocked
- **AND** Shizuku is not running
- **THEN** the app logs a warning but takes no action (policy will be applied when user next opens the app with Shizuku running)

### Requirement: Request Bluetooth permission at runtime
The app SHALL request the `BLUETOOTH_CONNECT` runtime permission on first launch or when needed. The app SHALL explain why the permission is required and handle denial gracefully.

#### Scenario: Permission granted
- **WHEN** the user grants `BLUETOOTH_CONNECT` permission
- **THEN** the app proceeds to display paired devices

#### Scenario: Permission denied
- **WHEN** the user denies `BLUETOOTH_CONNECT` permission
- **THEN** the app displays a message explaining that the permission is required to manage Bluetooth devices
