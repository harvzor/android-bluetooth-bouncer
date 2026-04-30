## MODIFIED Requirements

### Requirement: Show device connection status
The app SHALL indicate whether each paired device is currently connected. The connection indicator SHALL update in real time while the app is in the foreground — when a device connects or disconnects, the indicator SHALL reflect the new state within 1 second without requiring the user to restart the app or manually refresh.

#### Scenario: Connected device is indicated
- **WHEN** a paired device is currently connected via any Bluetooth profile
- **THEN** the device entry displays a "Connected" indicator

#### Scenario: Disconnected device is indicated
- **WHEN** a paired device is paired but not currently connected
- **THEN** the device entry does not display a "Connected" indicator

#### Scenario: Device connects while viewing the list
- **WHEN** the user is viewing the device list and a paired device establishes a connection
- **THEN** the "Connected" indicator appears on that device's entry without user intervention

#### Scenario: Device disconnects while viewing the list
- **WHEN** the user is viewing the device list and a connected device disconnects
- **THEN** the "Connected" indicator is removed from that device's entry without user intervention
