### Requirement: Derive notification state reactively from device presence and Room state
The app SHALL maintain an in-memory set of "nearby" device MAC addresses and combine it with the Room blocked-devices flow to automatically post or cancel the correct notification for each device. No call site that changes `isTemporarilyAllowed` or presence state SHALL be required to manually invoke `WatchNotificationHelper`. The observer SHALL run in an Application-scoped coroutine so it is active for the full process lifetime.

#### Scenario: Device becomes temporarily allowed via any path
- **WHEN** `isTemporarilyAllowed` is set to true in Room for a device whose MAC is in the nearby set
- **THEN** the app posts a "Temporarily connected" notification with a "Disconnect" action button for that device
- **THEN** the notification replaces any existing notification for that device in-place (same notification ID)
- **THEN** no new sound, vibration, or heads-up banner is produced

#### Scenario: Device is re-blocked via any path while nearby
- **WHEN** `isTemporarilyAllowed` is set to false in Room for a device whose MAC is in the nearby set
- **AND** `isAlertEnabled` is true for that device
- **THEN** the app posts a "Nearby — tap to allow for this session" notification with an "Allow temporarily" action button
- **THEN** the notification replaces any existing notification for that device in-place

#### Scenario: Device enters nearby set with Alert enabled and not temporarily allowed
- **WHEN** a device MAC is added to the nearby set
- **AND** `isAlertEnabled` is true for that device in Room
- **AND** `isTemporarilyAllowed` is false
- **THEN** the app posts a "Nearby" notification for that device

#### Scenario: Device enters nearby set while already temporarily allowed
- **WHEN** a device MAC is added to the nearby set
- **AND** `isTemporarilyAllowed` is true for that device in Room
- **THEN** the app posts a "Temporarily connected" notification for that device

#### Scenario: Device leaves nearby set
- **WHEN** a device MAC is removed from the nearby set
- **THEN** the app cancels the notification for that device (if any)

#### Scenario: Device has CDM association but Alert is disabled and not temporarily allowed
- **WHEN** a device MAC is in the nearby set
- **AND** `isAlertEnabled` is false
- **AND** `isTemporarilyAllowed` is false
- **THEN** no notification is posted for that device
