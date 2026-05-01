## ADDED Requirements

### Requirement: Temporarily-connected status in DeviceUiModel
The `DeviceUiModel` SHALL include an `isTemporarilyAllowed: Boolean` field. `DeviceListViewModel.refreshDeviceList()` SHALL populate this field from the corresponding `BlockedDeviceEntity.isTemporarilyAllowed` value. For devices not present in the blocked-devices table, the field SHALL default to `false`.

#### Scenario: Temporarily-allowed device is in blocked devices list
- **WHEN** `refreshDeviceList()` is called and a `BlockedDeviceEntity` with `isTemporarilyAllowed = true` exists for a device
- **THEN** the corresponding `DeviceUiModel` has `isTemporarilyAllowed = true`

#### Scenario: Device not in blocked list
- **WHEN** `refreshDeviceList()` is called and no `BlockedDeviceEntity` exists for a device
- **THEN** the corresponding `DeviceUiModel` has `isTemporarilyAllowed = false`

### Requirement: "Temporarily connected" status label
When a device is both connected and temporarily allowed, `DeviceRow` SHALL display a "Temporarily connected" label in amber (`0xFFFF9800`) in place of the standard "Connected" label.

#### Scenario: Connected and temporarily allowed
- **WHEN** a `DeviceUiModel` has `isConnected = true` and `isTemporarilyAllowed = true`
- **THEN** `DeviceRow` displays the label "Temporarily connected" in amber
- **THEN** `DeviceRow` does NOT display "Connected"

#### Scenario: Connected but not temporarily allowed
- **WHEN** a `DeviceUiModel` has `isConnected = true` and `isTemporarilyAllowed = false`
- **THEN** `DeviceRow` displays the label "Connected" in green
- **THEN** `DeviceRow` does NOT display "Temporarily connected"

#### Scenario: Detected only (not connected, temporarily allowed)
- **WHEN** a `DeviceUiModel` has `isConnected = false`, `isDetected = true`, and `isTemporarilyAllowed = true`
- **THEN** `DeviceRow` displays "Detected" (no special label for this transient state)
