## MODIFIED Requirements

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
