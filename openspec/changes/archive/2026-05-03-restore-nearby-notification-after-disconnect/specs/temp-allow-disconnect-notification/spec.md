## MODIFIED Requirements

### Requirement: Disconnect a temporarily allowed device via the notification
The app SHALL provide a one-tap "Disconnect" action in the "temporarily allowed" notification. Tapping it SHALL force-disconnect the device, re-apply `CONNECTION_POLICY_FORBIDDEN`, clear the temporary-allow flag in the database, and replace the notification with the "Nearby — tap to allow for this session" notification — all without requiring the user to open the app. The restored "nearby" notification SHALL NOT produce a new heads-up banner, sound, or vibration.

#### Scenario: User taps "Disconnect" in the notification
- **WHEN** the user taps the "Disconnect" action on the "temporarily allowed" notification
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

### Requirement: Cancel "temporarily allowed" notification when user disconnects from app UI
When the user disconnects a temporarily allowed device using the in-app Disconnect button, the app SHALL replace the "temporarily allowed" notification with the "Nearby — tap to allow for this session" notification. The restored notification SHALL NOT produce a new heads-up banner, sound, or vibration.

#### Scenario: User disconnects a temporarily allowed device from app UI
- **WHEN** the user taps the Disconnect button for a temporarily allowed device in the app
- **AND** the disconnect and re-block operations succeed
- **THEN** the "temporarily allowed" notification is replaced in-place with the "Nearby" notification containing an "Allow temporarily" action button
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: In-app disconnect fails
- **WHEN** the user taps Disconnect for a temporarily allowed device
- **AND** the disconnect operation fails
- **THEN** the "temporarily allowed" notification is NOT changed
