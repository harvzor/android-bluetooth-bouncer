## MODIFIED Requirements

### Requirement: Unblock a device to allow auto-connecting
The app SHALL allow the user to unblock a previously blocked device. When unblocked, the app SHALL set `CONNECTION_POLICY_ALLOWED` on all supported Bluetooth profiles for that device. The device SHALL resume normal auto-connection behavior. If the device has an active CDM association (Watch was enabled), the app SHALL stop observing device presence and disassociate from `CompanionDeviceManager` before deleting the device record.

#### Scenario: User unblocks a device
- **WHEN** the user toggles a blocked device to "allowed"
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on A2DP, Headset, and HID Host profiles
- **THEN** the device's record is removed from the local database
- **THEN** the toggle visually reflects the allowed state

#### Scenario: User unblocks a watched device
- **WHEN** the user toggles a blocked device to "allowed"
- **AND** the device has a non-null `cdmAssociationId`
- **THEN** the app calls `stopObservingDevicePresence(associationId)`
- **THEN** the app calls `CompanionDeviceManager.disassociate(associationId)`
- **THEN** the device's record is removed from the local database
- **THEN** no further presence notifications are posted for that device

### Requirement: Persist blocked device list
The app SHALL persist the list of blocked devices in a local Room database. The blocked state SHALL survive app restarts and device reboots. Each record SHALL store at minimum the device MAC address, device name at the time of blocking, a nullable CDM association ID (`cdmAssociationId: Int?`), and a temporary-allow flag (`isTemporarilyAllowed: Boolean`, default false).

#### Scenario: Blocked devices survive app restart
- **WHEN** the user has blocked devices and restarts the app
- **THEN** the device list shows the correct blocked/allowed state for all paired devices based on persisted data

#### Scenario: Paired device no longer exists
- **WHEN** a device in the blocked list has been unpaired from the phone
- **THEN** the device is not shown in the main list (only currently paired devices are shown)

#### Scenario: Watch and temporary-allow state survive app restart
- **WHEN** the app is restarted while a device has `cdmAssociationId` set or `isTemporarilyAllowed` true
- **THEN** both fields are correctly restored from the database
