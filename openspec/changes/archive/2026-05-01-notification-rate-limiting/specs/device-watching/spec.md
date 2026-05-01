## MODIFIED Requirements

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
