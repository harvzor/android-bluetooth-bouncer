## ADDED Requirements

### Requirement: Show "temporarily allowed" notification after UI-initiated connect
When the user connects a blocked device using the in-app Connect button and the operation succeeds, the app SHALL display a "Temporarily allowed" notification with a "Disconnect" action button for that device. This SHALL match the notification behaviour already specified for the notification-action path. The notification SHALL use the same notification ID as the "Nearby" notification so any existing entry in the shade is updated in-place with no new heads-up banner, sound, or vibration.

#### Scenario: User connects a blocked device via the app UI and succeeds
- **WHEN** the user taps the Connect button for a blocked device in the app
- **AND** `setConnectionPolicy(POLICY_ALLOWED)` succeeds
- **AND** `isTemporarilyAllowed` is set to true in Room
- **THEN** the app posts a "Temporarily allowed" notification with a "Disconnect" action button for that device
- **THEN** the notification uses the same notification ID as the preceding "Nearby" notification (`macAddress.hashCode()`)
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: User connects a blocked device via the app UI but connect fails
- **WHEN** the user taps the Connect button for a blocked device
- **AND** `setConnectionPolicy` or the connection attempt fails
- **THEN** the existing notification for that device is NOT changed
