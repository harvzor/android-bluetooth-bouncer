## MODIFIED Requirements

### Requirement: Automatically re-block when a temporarily allowed device leaves range
When a device that was temporarily allowed leaves Bluetooth range, the app SHALL automatically re-apply `CONNECTION_POLICY_FORBIDDEN` and clear the temporary-allow flag, returning the device to its normal blocked state. On success, the app SHALL also cancel any "temporarily allowed" notification for that device. This applies regardless of whether the temp-allow was triggered by the Connect button or by the "Allow temporarily" notification action.

#### Scenario: Temporarily allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is true
- **AND** the device is not currently profile-connected (A2DP or Headset active)
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`
- **THEN** the device MAC is removed from the nearby set immediately
- **THEN** the "temporarily allowed" notification for that device is cancelled

#### Scenario: Device disappears but is still profile-connected
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires
- **AND** the device still has an active A2DP or Headset profile connection
- **THEN** re-blocking is deferred until the profile connection also drops

#### Scenario: Non-temporarily-allowed device disappears
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a device
- **AND** `isTemporarilyAllowed` is false
- **THEN** the app schedules removal of the device MAC from the nearby set after `DETECTION_GRACE_PERIOD_MS`
- **THEN** if `onDeviceAppeared` fires for the same device before the grace period expires, the scheduled removal is cancelled and the notification remains visible without interruption
- **THEN** if the grace period expires without reappearance, the MAC is removed from the nearby set and the notification observer cancels the notification
