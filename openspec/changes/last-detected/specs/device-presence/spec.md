## MODIFIED Requirements

### Requirement: Detect paired devices with an active ACL link
The app SHALL determine, for each paired Bluetooth device, whether it has an active ACL connection regardless of whether any profile (A2DP, Headset, HID) is connected. A device is considered "detected" when it has an ACL link but is NOT profile-connected. This state is represented by the `isDetected` field on the device model. When a device was recently detected but its ACL link has since dropped, this is represented by the `lastDetectedSecondsAgo` field, which counts up from 0 to 29 over 30 seconds before expiring.

#### Scenario: Blocked device comes into range
- **WHEN** a blocked paired device establishes an ACL link (triggering `ACTION_ACL_CONNECTED`)
- **THEN** the device list refreshes within 1 second
- **THEN** the device row displays a "Detected" label
- **THEN** the device row does NOT display a "Connected" label

#### Scenario: Blocked device goes out of range — label decays
- **WHEN** a blocked paired device drops its ACL link (triggering `ACTION_ACL_DISCONNECTED`)
- **THEN** the device list refreshes within 1 second
- **THEN** the device row no longer displays the live "Detected" label
- **THEN** the device row displays "Detected Ns ago" where N increments each second
- **THEN** after 30 seconds the "Detected Ns ago" label disappears

#### Scenario: Allowed device has ACL but no active profile connection
- **WHEN** a paired, non-blocked device has an ACL link but no active A2DP or Headset profile connection
- **THEN** the device row displays a "Detected" label
- **THEN** the device row does NOT display a "Connected" label

#### Scenario: Connected device is not also shown as Detected
- **WHEN** a device has both an ACL link and an active profile connection
- **THEN** the device row displays only "Connected"
- **THEN** the device row does NOT display "Detected"

#### Scenario: Offline device shows neither label
- **WHEN** a paired device has no ACL link, no profile connection, and no active last-detected timestamp
- **THEN** the device row displays neither "Connected" nor "Detected"
