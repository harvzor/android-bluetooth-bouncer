## MODIFIED Requirements

### Requirement: Automatically re-block when a temporarily allowed device leaves range
When a device that was temporarily allowed leaves Bluetooth range, the app SHALL automatically re-apply `CONNECTION_POLICY_FORBIDDEN` and clear the temporary-allow flag, returning the device to its normal blocked state. On success, the app SHALL also cancel any notification for that device. This applies regardless of whether the temp-allow was triggered by the Connect button or by the "Allow temporarily" notification action. Additionally, when any alert-enabled device disappears, the app SHALL cancel any active notification for that device, regardless of `isTemporarilyAllowed` state, to prevent orphaned notifications left by prior manual disconnects.

#### Scenario: Temporarily allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is true
- **AND** the device is not currently profile-connected (A2DP or Headset active)
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`
- **THEN** any notification for that device is cancelled

#### Scenario: Device disappears but is still profile-connected
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires
- **AND** the device still has an active A2DP or Headset profile connection
- **THEN** re-blocking is deferred until the profile connection also drops

#### Scenario: Non-temporarily-allowed alert-enabled device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is false
- **AND** `isAlertEnabled` is true
- **THEN** any active notification for that device is cancelled
- **THEN** no re-blocking action is taken (device is already blocked)

#### Scenario: Non-temporarily-allowed non-alert device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is false
- **AND** `isAlertEnabled` is false
- **THEN** no action is taken
