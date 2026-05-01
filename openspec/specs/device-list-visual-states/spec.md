### Requirement: Connected device icon is Bluetooth Blue
When a device is connected, the Bluetooth icon SHALL be tinted with the Bluetooth SIG brand color (`#0082FC`). The icon SHALL NOT display a Badge dot overlay.

#### Scenario: Connected device icon tint
- **WHEN** a device has `isConnected = true`
- **THEN** the Bluetooth icon is tinted `Color(0xFF0082FC)`
- **THEN** no Badge dot is rendered on the icon

#### Scenario: Non-connected device icon tint
- **WHEN** a device has `isConnected = false`
- **THEN** the Bluetooth icon is tinted `onSurfaceVariant` (default)
- **THEN** no Badge dot is rendered on the icon

### Requirement: Connected status text is Bluetooth Blue
The "Connected" and "Temporarily connected" status labels SHALL be rendered in Bluetooth Blue (`#0082FC`).

#### Scenario: Connected label color
- **WHEN** a device has `isConnected = true` and `isTemporarilyAllowed = false`
- **THEN** the "Connected" text is colored `Color(0xFF0082FC)`

#### Scenario: Temporarily connected label color
- **WHEN** a device has `isConnected = true` and `isTemporarilyAllowed = true`
- **THEN** the "Temporarily connected" text is colored `Color(0xFF0082FC)`

### Requirement: Detected status text is hardcoded salmon-orange
The "Detected" and "Detected Xs ago" status labels SHALL be rendered in a fixed salmon-orange color (`#E8A06C`), independent of the dynamic theme.

#### Scenario: Detected label color
- **WHEN** a device has `isDetected = true` and `isConnected = false`
- **THEN** the "Detected" text is colored `Color(0xFFE8A06C)`

#### Scenario: Stale detection label color
- **WHEN** a device has a non-null `lastDetectedSecondsAgo` and `isConnected = false`
- **THEN** the "Detected Xs ago" text is colored `Color(0xFFE8A06C)`
