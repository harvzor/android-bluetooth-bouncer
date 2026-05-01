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

### Requirement: Show confirmation snackbar when Alert is enabled
When the user successfully enables the Alert toggle on a blocked device, the app SHALL display a Snackbar message confirming the behavior. The Snackbar SHALL appear every time Alert is enabled, not only on first use. No Snackbar SHALL be shown when Alert is disabled.

#### Scenario: User enables Alert successfully
- **WHEN** the user enables the Alert toggle on a blocked device
- **AND** the CDM association dialog is confirmed
- **AND** `enableWatch()` succeeds
- **THEN** the app shows a confirmation Snackbar

#### Scenario: User enables Alert but flow fails
- **WHEN** the user enables the Alert toggle on a blocked device
- **AND** the CDM association or `enableWatch()` fails
- **THEN** no confirmation Snackbar is shown (the existing error Snackbar is shown instead)

#### Scenario: User disables Alert
- **WHEN** the user disables the Alert toggle on a blocked device
- **THEN** no Snackbar is shown

### Requirement: Notify when a watched blocked device appears
When a watched blocked device comes into Bluetooth range, the app SHALL post a notification offering the user a temporary allow action. The `CompanionDeviceService` SHALL be the entry point for this event, running even when the app is not in the foreground. Repeated `onDeviceAppeared` callbacks for a device whose notification is already visible SHALL NOT re-trigger sound, vibration, or heads-up.

#### Scenario: Watched blocked device appears
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a watched device
- **AND** the device is still blocked in the database (`cdmAssociationId` is non-null)
- **AND** `isTemporarilyAllowed` is false
- **THEN** the app posts a notification with the device name and an "Allow temporarily" action button

#### Scenario: Watched device appears but is already temporarily allowed
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a watched device
- **AND** `isTemporarilyAllowed` is true
- **THEN** no notification is posted

#### Scenario: Device oscillates — notification already showing
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a watched blocked device
- **AND** a notification for that device is already visible in the notification drawer
- **THEN** the notification is silently updated (no sound, no vibration, no heads-up)

### Requirement: Allow a blocked device temporarily from a notification
The app SHALL provide a one-tap "Allow temporarily" action in the presence notification. Tapping it SHALL call `setConnectionPolicy(CONNECTION_POLICY_ALLOWED)` via Shizuku and mark the device as temporarily allowed in the database, without requiring the user to open the app.

#### Scenario: User taps "Allow temporarily"
- **WHEN** the user taps the "Allow temporarily" action on the presence notification
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to true in `BlockedDeviceEntity`
- **THEN** the notification is dismissed
- **THEN** the device is able to connect normally

#### Scenario: Shizuku is not running when "Allow temporarily" is tapped
- **WHEN** the user taps "Allow temporarily" but Shizuku is not running
- **THEN** the app posts an error notification: "Could not allow — Shizuku is not running"
- **THEN** `isTemporarilyAllowed` remains false

### Requirement: Automatically re-block when a temporarily allowed device leaves range
When a device that was temporarily allowed leaves Bluetooth range, the app SHALL automatically re-apply `CONNECTION_POLICY_FORBIDDEN` and clear the temporary-allow flag, returning the device to its normal blocked state.

#### Scenario: Temporarily allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is true
- **AND** the device is not currently profile-connected (A2DP or Headset active)
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`

#### Scenario: Device disappears but is still profile-connected
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires
- **AND** the device still has an active A2DP or Headset profile connection
- **THEN** re-blocking is deferred until the profile connection also drops

#### Scenario: Non-temporarily-allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is false
- **THEN** no action is taken (device is already blocked)

### Requirement: Disable Watch for a blocked device
The user SHALL be able to disable the Alert toggle for a blocked device. Doing so SHALL stop presence observation and remove the CDM association for that device.

#### Scenario: User disables Alert
- **WHEN** the user disables the Alert toggle on a blocked device
- **THEN** the app calls `stopObservingDevicePresence(associationId)`
- **THEN** the app calls `CompanionDeviceManager.disassociate(associationId)`
- **THEN** `cdmAssociationId` is set to null in `BlockedDeviceEntity`
- **THEN** no further presence notifications are posted for that device
