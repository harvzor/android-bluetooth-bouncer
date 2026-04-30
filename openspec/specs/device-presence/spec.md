### Requirement: Detect paired devices with an active ACL link
The app SHALL determine, for each paired Bluetooth device, whether it has an active ACL connection regardless of whether any profile (A2DP, Headset, HID) is connected. A device is considered "detected" when it has an ACL link but is NOT profile-connected. This state is represented by the `isDetected` field on the device model.

#### Scenario: Blocked device comes into range
- **WHEN** a blocked paired device establishes an ACL link (triggering `ACTION_ACL_CONNECTED`)
- **THEN** the device list refreshes within 1 second
- **THEN** the device row displays a "Detected" label
- **THEN** the device row does NOT display a "Connected" label

#### Scenario: Blocked device goes out of range
- **WHEN** a blocked paired device drops its ACL link (triggering `ACTION_ACL_DISCONNECTED`)
- **THEN** the device list refreshes within 1 second
- **THEN** the device row no longer displays the "Detected" label

#### Scenario: Allowed device has ACL but no active profile connection
- **WHEN** a paired, non-blocked device has an ACL link but no active A2DP or Headset profile connection
- **THEN** the device row displays a "Detected" label
- **THEN** the device row does NOT display a "Connected" label

#### Scenario: Connected device is not also shown as Detected
- **WHEN** a device has both an ACL link and an active profile connection
- **THEN** the device row displays only "Connected"
- **THEN** the device row does NOT display "Detected"

#### Scenario: Offline device shows neither label
- **WHEN** a paired device has no ACL link and no profile connection
- **THEN** the device row displays neither "Connected" nor "Detected"

### Requirement: Device list sort order reflects presence tier
The device list SHALL be sorted with connected devices first, detected devices second, and offline devices last. Within each tier devices SHALL be sorted alphabetically by name.

#### Scenario: Mixed presence states
- **WHEN** the device list contains a mix of connected, detected, and offline devices
- **THEN** all connected devices appear before all detected devices
- **THEN** all detected devices appear before all offline devices
- **THEN** devices within each tier are ordered alphabetically by name
