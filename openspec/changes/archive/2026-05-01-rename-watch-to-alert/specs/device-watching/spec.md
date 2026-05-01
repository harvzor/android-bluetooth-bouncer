## ADDED Requirements

### Requirement: Show confirmation snackbar when Alert is enabled
When the user successfully enables the Alert toggle on a blocked device, the app SHALL display a Snackbar message confirming the behavior: "When Alert is enabled, you'll get a notification whenever this blocked device appears in Bluetooth range. The device stays blocked -- you'll just be alerted." The Snackbar SHALL appear every time Alert is enabled, not only on first use. No Snackbar SHALL be shown when Alert is disabled.

#### Scenario: User enables Alert successfully
- **WHEN** the user enables the Alert toggle on a blocked device
- **AND** the CDM association dialog is confirmed
- **AND** `enableWatch()` succeeds
- **THEN** the app shows a Snackbar: "When Alert is enabled, you'll get a notification whenever this blocked device appears in Bluetooth range. The device stays blocked -- you'll just be alerted."

#### Scenario: User enables Alert but flow fails
- **WHEN** the user enables the Alert toggle on a blocked device
- **AND** the CDM association or `enableWatch()` fails
- **THEN** no confirmation Snackbar is shown (the existing error Snackbar is shown instead)

#### Scenario: User disables Alert
- **WHEN** the user disables the Alert toggle on a blocked device
- **THEN** no Snackbar is shown

## MODIFIED Requirements

### Requirement: Watch a blocked device for background presence
The app SHALL allow users to opt individual blocked devices into background presence monitoring. When a device is "watched", the app SHALL register it with `CompanionDeviceManager` and call `startObservingDevicePresence()` so the OS can wake the app when that device comes into Bluetooth range, even if the app process is not running. This feature SHALL only be available on API 33 and above. The UI toggle for this feature SHALL be labelled "Alert".

#### Scenario: User enables Alert on a blocked device
- **WHEN** the user enables the Alert toggle on a blocked device (API 33+)
- **THEN** the app initiates a `CompanionDeviceManager.associate()` flow for that device's MAC address
- **THEN** the system shows a confirmation dialog to the user
- **WHEN** the user confirms
- **THEN** the app calls `startObservingDevicePresence(associationId)`
- **THEN** the `cdmAssociationId` is persisted in the device's `BlockedDeviceEntity`

#### Scenario: Watch association fails — device not nearby
- **WHEN** the user enables the Alert toggle but the device is not in Bluetooth range
- **THEN** the system dialog times out or returns no result
- **THEN** the app shows a message: "Device not found nearby. Try again when it's in Bluetooth range."
- **THEN** the Alert toggle remains off

#### Scenario: User cancels the Alert confirmation dialog
- **WHEN** the user enables the Alert toggle but dismisses the system confirmation dialog
- **THEN** the Alert toggle remains off
- **THEN** no CDM association is created

#### Scenario: Alert toggle hidden on API < 33
- **WHEN** the device is running Android 12 (API 31 or 32)
- **THEN** the Alert toggle is not shown for any device

### Requirement: Disable Watch for a blocked device
The user SHALL be able to disable the Alert toggle for a blocked device. Doing so SHALL stop presence observation and remove the CDM association for that device.

#### Scenario: User disables Alert
- **WHEN** the user disables the Alert toggle on a blocked device
- **THEN** the app calls `stopObservingDevicePresence(associationId)`
- **THEN** the app calls `CompanionDeviceManager.disassociate(associationId)`
- **THEN** `cdmAssociationId` is set to null in `BlockedDeviceEntity`
- **THEN** no further presence notifications are posted for that device
