## MODIFIED Requirements

### Requirement: Persist blocked device list
The app SHALL persist the list of blocked devices in a local Room database. The blocked state SHALL survive app restarts and device reboots. Each record SHALL store at minimum the device MAC address, device name at the time of blocking, a nullable CDM association ID (`cdmAssociationId: Int?`), a temporary-allow flag (`isTemporarilyAllowed: Boolean`, default false), and an alert-enabled flag (`isAlertEnabled: Boolean`, default false). The database schema is at version 1. No migration history is maintained.

#### Scenario: Blocked devices survive app restart
- **WHEN** the user has blocked devices and restarts the app
- **THEN** the device list shows the correct blocked/allowed state for all paired devices based on persisted data

#### Scenario: Paired device no longer exists
- **WHEN** a device in the blocked list has been unpaired from the phone
- **THEN** the device is not shown in the main list (only currently paired devices are shown)

#### Scenario: Watch, alert, and temporary-allow state survive app restart
- **WHEN** the app is restarted while a device has `cdmAssociationId` set, `isAlertEnabled` true, or `isTemporarilyAllowed` true
- **THEN** all three fields are correctly restored from the database
