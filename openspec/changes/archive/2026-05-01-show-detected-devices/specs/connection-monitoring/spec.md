## MODIFIED Requirements

### Requirement: Observe Bluetooth ACL connection events
The app SHALL register a BroadcastReceiver for `ACTION_ACL_CONNECTED` and `ACTION_ACL_DISCONNECTED` while the device list screen is active. When either event is received, the app SHALL refresh the device list so that connection indicators AND detection indicators reflect current state.

#### Scenario: Device connects while app is open
- **WHEN** a paired Bluetooth device establishes an ACL connection while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the newly connected device displays the "Connected" indicator

#### Scenario: Device disconnects while app is open
- **WHEN** a connected Bluetooth device drops its ACL connection while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the disconnected device no longer displays the "Connected" indicator
- **THEN** if the device retains an ACL link (e.g. it is blocked), it displays the "Detected" indicator instead

#### Scenario: Multiple devices change state rapidly
- **WHEN** multiple Bluetooth connection events fire within a short time window (e.g., Bluetooth toggled off)
- **THEN** the app debounces the events and refreshes the device list once rather than per-event

#### Scenario: Blocked device comes into ACL range
- **WHEN** a blocked paired device establishes an ACL link while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the device displays the "Detected" indicator
- **THEN** the device does NOT display the "Connected" indicator
