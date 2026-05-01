### Requirement: Device list is divided into four named sections
The device list SHALL be rendered as a sectioned list with up to four sections in display order: **Connected**, **Detected**, **Blocked**, **Allowed**. Each section SHALL only appear when it contains at least one device.

#### Scenario: All four sections present
- **WHEN** at least one device exists in each of the four states
- **THEN** four section headers are rendered in order: Connected, Detected, Blocked, Allowed

#### Scenario: Empty section is hidden
- **WHEN** no devices belong to a particular section
- **THEN** that section's header is not rendered and no empty space is left for it

### Requirement: Each device belongs to exactly one section based on its most-active state
Section assignment SHALL follow a priority order: Connected > Detected (including recently detected) > Blocked > Allowed. A device SHALL appear in exactly one section.

#### Scenario: Connected device appears in Connected section
- **WHEN** a device has `isConnected = true`
- **THEN** it appears under the Connected section header regardless of other state

#### Scenario: Detected device appears in Detected section
- **WHEN** a device has `isDetected = true` and `isConnected = false`
- **THEN** it appears under the Detected section header

#### Scenario: Recently-detected device appears in Detected section
- **WHEN** a device has `lastDetectedSecondsAgo != null` and `isConnected = false`
- **THEN** it appears under the Detected section header (not Blocked or Allowed)

#### Scenario: Undetected blocked device appears in Blocked section
- **WHEN** a device has `isBlocked = true`, `isConnected = false`, and `lastDetectedSecondsAgo == null` and `isDetected = false`
- **THEN** it appears under the Blocked section header

#### Scenario: Undetected allowed device appears in Allowed section
- **WHEN** a device has `isBlocked = false`, `isConnected = false`, `isDetected = false`, and `lastDetectedSecondsAgo == null`
- **THEN** it appears under the Allowed section header

### Requirement: Devices are sorted alphabetically by name within each section, with live-detected above recently-detected
Within each section, devices SHALL be sorted alphabetically by name. Within the Detected section specifically, devices with `isDetected = true` SHALL sort above devices with `lastDetectedSecondsAgo != null`.

#### Scenario: Alphabetical order within Blocked section
- **WHEN** multiple blocked devices are in the Blocked section
- **THEN** they are ordered A–Z by display name

#### Scenario: Live-detected before recently-detected in Detected section
- **WHEN** the Detected section contains both live-detected (`isDetected = true`) and recently-detected (`lastDetectedSecondsAgo != null`) devices
- **THEN** live-detected devices appear above recently-detected devices
- **THEN** each sub-group is sorted alphabetically by name
