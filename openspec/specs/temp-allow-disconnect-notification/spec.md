### Requirement: Show "temporarily connected" notification with Disconnect action after Allow temporarily
After the "Allow temporarily" notification action succeeds, the app SHALL replace the "nearby" notification with a "temporarily connected" notification for the same device. The replacement SHALL use the same notification ID so the entry in the notification shade is updated in-place. The notification SHALL include a "Disconnect" action button that force-disconnects the device and restores the blocked state without opening the app. The notification SHALL NOT produce a second heads-up banner, sound, or vibration. The notification SHALL be swipe-dismissible (not ongoing).

#### Scenario: "Allow temporarily" succeeds
- **WHEN** `TemporaryAllowReceiver` handles an `ACTION_ALLOW_TEMPORARILY` broadcast successfully
- **THEN** the app posts a notification with the device name as the title and "Temporarily connected" as the content text
- **THEN** the notification has a "Disconnect" action button wired to `DisconnectReceiver`
- **THEN** the notification uses the same notification ID as the preceding "nearby" notification (`macAddress.hashCode()`)
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: User swipe-dismisses the "temporarily connected" notification
- **WHEN** the user swipes away the "temporarily connected" notification
- **THEN** the notification is removed
- **THEN** the device remains connected and `isTemporarilyAllowed` remains true (no implicit disconnect)

### Requirement: Disconnect a temporarily connected device via the notification
The app SHALL provide a one-tap "Disconnect" action in the "temporarily connected" notification. Tapping it SHALL force-disconnect the device, re-apply `CONNECTION_POLICY_FORBIDDEN`, clear the temporary-allow flag in the database, and replace the notification with the "Nearby — tap to allow for this session" notification — all without requiring the user to open the app. The restored "nearby" notification SHALL NOT produce a new heads-up banner, sound, or vibration.

#### Scenario: User taps "Disconnect" in the notification
- **WHEN** the user taps the "Disconnect" action on the "temporarily connected" notification
- **THEN** the app calls `disconnectDevice(macAddress)` via Shizuku
- **THEN** the app calls `setConnectionPolicy(macAddress, CONNECTION_POLICY_FORBIDDEN)` via Shizuku
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`
- **THEN** the notification is replaced in-place with the "Nearby" notification containing an "Allow temporarily" action button
- **THEN** no new sound, vibration, or heads-up banner is produced
- **THEN** the device can no longer connect

#### Scenario: Shizuku is not available when "Disconnect" is tapped
- **WHEN** the user taps "Disconnect" but Shizuku is not running
- **THEN** the app posts an error notification: "Could not disconnect — Shizuku unavailable"
- **THEN** `isTemporarilyAllowed` remains true and the device remains connected

### Requirement: Show "temporarily connected" notification after UI-initiated connect
When the user connects a blocked device using the in-app Connect button and the operation succeeds, the app SHALL display a "Temporarily connected" notification with a "Disconnect" action button for that device. This SHALL match the notification behaviour already specified for the notification-action path. The notification SHALL use the same notification ID as the "Nearby" notification so any existing entry in the shade is updated in-place with no new heads-up banner, sound, or vibration.

#### Scenario: User connects a blocked device via the app UI and succeeds
- **WHEN** the user taps the Connect button for a blocked device in the app
- **AND** `setConnectionPolicy(POLICY_ALLOWED)` succeeds
- **AND** `isTemporarilyAllowed` is set to true in Room
- **THEN** the app posts a "Temporarily connected" notification with a "Disconnect" action button for that device
- **THEN** the notification uses the same notification ID as the preceding "Nearby" notification (`macAddress.hashCode()`)
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: User connects a blocked device via the app UI but connect fails
- **WHEN** the user taps the Connect button for a blocked device
- **AND** `setConnectionPolicy` or the connection attempt fails
- **THEN** the existing notification for that device is NOT changed

### Requirement: Auto-dismiss "temporarily connected" notification when device leaves range
When a temporarily allowed device triggers `onDeviceDisappeared` and is successfully re-blocked, the app SHALL cancel the "temporarily connected" notification. This ensures the notification does not remain visible in the shade after the device has gone out of range.

#### Scenario: Temporarily allowed device leaves range and is successfully re-blocked
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a temporarily allowed device
- **AND** the device is not currently profile-connected
- **AND** `setConnectionPolicy(POLICY_FORBIDDEN)` succeeds
- **THEN** the "temporarily connected" notification for that device is cancelled

#### Scenario: Re-block fails on device disappear
- **WHEN** `CompanionDeviceService.onDeviceDisappeared()` fires for a temporarily allowed device
- **AND** `setConnectionPolicy(POLICY_FORBIDDEN)` fails
- **THEN** the "temporarily connected" notification is NOT cancelled (reflects that the device may still reconnect)

### Requirement: Cancel "temporarily connected" notification when user disconnects from app UI
When the user disconnects a temporarily connected device using the in-app Disconnect button, the app SHALL replace the "temporarily connected" notification with the "Nearby — tap to allow for this session" notification. The restored notification SHALL NOT produce a new heads-up banner, sound, or vibration.

#### Scenario: User disconnects a temporarily connected device from app UI
- **WHEN** the user taps the Disconnect button for a temporarily connected device in the app
- **AND** the disconnect and re-block operations succeed
- **THEN** the "temporarily connected" notification is replaced in-place with the "Nearby" notification containing an "Allow temporarily" action button
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: In-app disconnect fails
- **WHEN** the user taps Disconnect for a temporarily connected device
- **AND** the disconnect operation fails
- **THEN** the "temporarily connected" notification is NOT changed
