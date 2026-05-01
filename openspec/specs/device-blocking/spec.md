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
The app SHALL allow the user to unblock a previously blocked device. When unblocked, the app SHALL set `CONNECTION_POLICY_ALLOWED` on all supported Bluetooth profiles for that device. The device SHALL resume normal auto-connection behaviour. If the device has an active CDM association (`cdmAssociationId` is non-null), the app SHALL stop observing device presence, disassociate from `CompanionDeviceManager`, and clear `cdmAssociationId` before deleting the device record. This applies regardless of whether the association was created by an Alert enable or a Connect action.

#### Scenario: User unblocks a device
- **WHEN** the user toggles a blocked device to "allowed"
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on A2DP, Headset, and HID Host profiles
- **THEN** the device's record is removed from the local database
- **THEN** the toggle visually reflects the allowed state

#### Scenario: User unblocks a device with a CDM association
- **WHEN** the user toggles a blocked device to "allowed"
- **AND** the device has a non-null `cdmAssociationId` (whether from Alert or Connect)
- **THEN** the app calls `stopObservingDevicePresence(associationId)`
- **THEN** the app calls `CompanionDeviceManager.disassociate(associationId)`
- **THEN** `cdmAssociationId` is cleared
- **THEN** the device's record is removed from the local database
- **THEN** no further presence notifications or auto-revert actions occur for that device

### Requirement: Persist blocked device list
The app SHALL persist the list of blocked devices in a local Room database. The blocked state SHALL survive app restarts and device reboots. Each record SHALL store at minimum the device MAC address, device name at the time of blocking, a nullable CDM association ID (`cdmAssociationId: Int?`), a temporary-allow flag (`isTemporarilyAllowed: Boolean`, default false), and an alert-enabled flag (`isAlertEnabled: Boolean`, default false). The database schema is at version 3. Migration from version 2 SHALL add the `isAlertEnabled` column and backfill it to true for all rows where `cdmAssociationId IS NOT NULL`.

#### Scenario: Blocked devices survive app restart
- **WHEN** the user has blocked devices and restarts the app
- **THEN** the device list shows the correct blocked/allowed state for all paired devices based on persisted data

#### Scenario: Paired device no longer exists
- **WHEN** a device in the blocked list has been unpaired from the phone
- **THEN** the device is not shown in the main list (only currently paired devices are shown)

#### Scenario: Watch, alert, and temporary-allow state survive app restart
- **WHEN** the app is restarted while a device has `cdmAssociationId` set, `isAlertEnabled` true, or `isTemporarilyAllowed` true
- **THEN** all three fields are correctly restored from the database

#### Scenario: Migration from version 2 preserves Alert state
- **WHEN** the app is upgraded from a version with database schema version 2
- **AND** a device row has a non-null `cdmAssociationId`
- **THEN** after migration, that row has `isAlertEnabled = true`
- **WHEN** a device row has a null `cdmAssociationId`
- **THEN** after migration, that row has `isAlertEnabled = false`

### Requirement: Show device connection status
The app SHALL indicate whether each paired device is currently connected. The connection indicator SHALL update in real time while the app is in the foreground — when a device connects or disconnects, the indicator SHALL reflect the new state within 1 second without requiring the user to restart the app or manually refresh.

#### Scenario: Connected device is indicated
- **WHEN** a paired device is currently connected via any Bluetooth profile
- **THEN** the device entry displays a "Connected" indicator

#### Scenario: Disconnected device is indicated
- **WHEN** a paired device is paired but not currently connected
- **THEN** the device entry does not display a "Connected" indicator

#### Scenario: Device connects while viewing the list
- **WHEN** the user is viewing the device list and a paired device establishes a connection
- **THEN** the "Connected" indicator appears on that device's entry without user intervention

#### Scenario: Device disconnects while viewing the list
- **WHEN** the user is viewing the device list and a connected device disconnects
- **THEN** the "Connected" indicator is removed from that device's entry without user intervention

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
