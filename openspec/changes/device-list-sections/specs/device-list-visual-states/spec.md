## MODIFIED Requirements

### Requirement: Detected status text is hardcoded salmon-orange
The "Detected" and "Detected recently" status labels SHALL be rendered in a fixed salmon-orange color (`#E8A06C`), independent of the dynamic theme.

#### Scenario: Detected label color
- **WHEN** a device has `isDetected = true` and `isConnected = false`
- **THEN** the "Detected" text is colored `Color(0xFFE8A06C)`

#### Scenario: Stale detection label color
- **WHEN** a device has a non-null `lastDetectedSecondsAgo` and `isConnected = false`
- **THEN** the "Detected recently" text is colored `Color(0xFFE8A06C)`
