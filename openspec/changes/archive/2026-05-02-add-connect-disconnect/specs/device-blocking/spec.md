## MODIFIED Requirements

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
