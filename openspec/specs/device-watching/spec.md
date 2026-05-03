### Requirement: Watch a blocked device for background presence
The app SHALL allow users to opt individual blocked devices into background presence monitoring. When a device is "watched", the app SHALL register it with `CompanionDeviceManager` and call `startObservingDevicePresence()` so the OS can wake the app when that device comes into Bluetooth range, even if the app process is not running. This feature SHALL only be available on API 33 and above. The UI toggle for this feature SHALL be labelled "Alert". A CDM association MAY already exist for the device (created by a prior Connect action); in that case, the association dialog SHALL NOT be shown again and the existing `cdmAssociationId` SHALL be reused.

#### Scenario: User enables Alert on a blocked device (no existing CDM association)
- **WHEN** the user enables the Alert toggle on a blocked device (API 33+)
- **AND** no CDM association exists for the device (`cdmAssociationId` is null)
- **THEN** the app initiates a `CompanionDeviceManager.associate()` flow for that device's MAC address
- **THEN** the system shows a confirmation dialog to the user
- **WHEN** the user confirms
- **THEN** the app calls `startObservingDevicePresence(associationId)`
- **THEN** the `cdmAssociationId` is persisted in `BlockedDeviceEntity`
- **THEN** `isAlertEnabled` is set to true in `BlockedDeviceEntity`

#### Scenario: User enables Alert on a blocked device (CDM association already exists from Connect)
- **WHEN** the user enables the Alert toggle on a blocked device (API 33+)
- **AND** a CDM association already exists (`cdmAssociationId` is non-null)
- **THEN** no system confirmation dialog is shown
- **THEN** the app calls `startObservingDevicePresence(associationId)` with the existing association ID
- **THEN** `isAlertEnabled` is set to true in `BlockedDeviceEntity`

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
When the user successfully enables the Alert toggle on a blocked device, the app SHALL display a Snackbar message confirming the behaviour. The Snackbar SHALL appear every time Alert is enabled, not only on first use. No Snackbar SHALL be shown when Alert is disabled.

#### Scenario: User enables Alert successfully
- **WHEN** the user enables the Alert toggle on a blocked device
- **AND** the CDM association dialog is confirmed (or skipped because association already existed)
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
When a watched blocked device comes into Bluetooth range, the app SHALL post a notification offering the user a temporary allow action. The `CompanionDeviceService` SHALL be the entry point for this event, running even when the app is not in the foreground. Whether to post a notification SHALL be gated on `isAlertEnabled`, not on `cdmAssociationId` presence (a device may have a CDM association for Connect purposes without wanting Alert notifications). Repeated `onDeviceAppeared` callbacks for a device whose notification is already visible SHALL NOT re-trigger sound, vibration, or heads-up.

#### Scenario: Watched blocked device appears with Alert enabled
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a device
- **AND** `isAlertEnabled` is true
- **AND** `isTemporarilyAllowed` is false
- **THEN** the app posts a notification with the device name and an "Allow temporarily" action button

#### Scenario: Device appears but Alert is disabled (CDM association exists for Connect only)
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a device
- **AND** `isAlertEnabled` is false
- **THEN** no notification is posted

#### Scenario: Watched device appears but is already temporarily allowed
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a watched device
- **AND** `isTemporarilyAllowed` is true
- **THEN** no notification is posted

#### Scenario: Device oscillates — notification already showing
- **WHEN** `CompanionDeviceService.onDeviceAppeared()` fires for a watched blocked device
- **AND** a notification for that device is already visible in the notification drawer
- **THEN** the notification is silently updated (no sound, no vibration, no heads-up)

### Requirement: Allow a blocked device temporarily from a notification
The app SHALL provide a one-tap "Allow temporarily" action in the presence notification. Tapping it SHALL call `setConnectionPolicy(CONNECTION_POLICY_ALLOWED)` via Shizuku and mark the device as temporarily allowed in the database, without requiring the user to open the app. On success, the presence notification SHALL be replaced with a "temporarily allowed" notification (see `temp-allow-disconnect-notification` spec).

#### Scenario: User taps "Allow temporarily"
- **WHEN** the user taps the "Allow temporarily" action on the presence notification
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to true in `BlockedDeviceEntity`
- **THEN** the presence notification is replaced in-place with a "temporarily allowed" notification containing a "Disconnect" action button
- **THEN** the device is able to connect normally

#### Scenario: Shizuku is not running when "Allow temporarily" is tapped
- **WHEN** the user taps "Allow temporarily" but Shizuku is not running
- **THEN** the app posts an error notification: "Could not allow — Shizuku is not running"
- **THEN** `isTemporarilyAllowed` remains false

### Requirement: Automatically re-block when a temporarily allowed device leaves range
When a device that was temporarily allowed leaves Bluetooth range, the app SHALL automatically re-apply `CONNECTION_POLICY_FORBIDDEN` and clear the temporary-allow flag, returning the device to its normal blocked state. On success, the app SHALL also cancel any "temporarily allowed" notification for that device. This applies regardless of whether the temp-allow was triggered by the Connect button or by the "Allow temporarily" notification action.

#### Scenario: Temporarily allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is true
- **AND** the device is not currently profile-connected (A2DP or Headset active)
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`
- **THEN** the "temporarily allowed" notification for that device is cancelled

#### Scenario: Device disappears but is still profile-connected
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires
- **AND** the device still has an active A2DP or Headset profile connection
- **THEN** re-blocking is deferred until the profile connection also drops

#### Scenario: Non-temporarily-allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is false
- **THEN** no action is taken (device is already blocked)

### Requirement: Disable Watch for a blocked device
The user SHALL be able to disable the Alert toggle for a blocked device. Doing so SHALL stop presence observation and set `isAlertEnabled` to false, but SHALL NOT disassociate the CDM association or clear `cdmAssociationId`. The CDM association is retained so that a future Connect tap does not require a new system confirmation dialog.

#### Scenario: User disables Alert
- **WHEN** the user disables the Alert toggle on a blocked device
- **THEN** the app calls `stopObservingDevicePresence(associationId)`
- **THEN** `isAlertEnabled` is set to false in `BlockedDeviceEntity`
- **THEN** `cdmAssociationId` is NOT cleared
- **THEN** no further presence notifications are posted for that device
- **THEN** the CDM association remains registered with the system
