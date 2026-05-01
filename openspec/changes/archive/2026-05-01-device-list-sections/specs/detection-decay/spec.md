## ADDED Requirements

### Requirement: Recently-detected devices remain in the Detected sort tier for the full decay window
A device with a non-null `lastDetectedSecondsAgo` SHALL be assigned to the Detected section (same tier as `isDetected = true`) for the entire 30-second decay window. It SHALL NOT drop to the Blocked or Allowed section until its last-detected timestamp is evicted.

#### Scenario: Device stays in Detected section immediately after ACL drop
- **WHEN** a device transitions from `isDetected = true` to `isDetected = false` (ACL drops)
- **THEN** `lastDetectedSecondsAgo` is 0
- **THEN** the device remains in the Detected section (not Blocked or Allowed)

#### Scenario: Device stays in Detected section during full decay
- **WHEN** a device has `lastDetectedSecondsAgo` in the range 1–29
- **THEN** the device is in the Detected section

#### Scenario: Device moves to Blocked or Allowed section after decay expires
- **WHEN** 30 seconds have elapsed and the last-detected entry is evicted (`lastDetectedSecondsAgo` becomes null)
- **THEN** the device moves to the Blocked section (if `isBlocked = true`) or the Allowed section (if `isBlocked = false`)
