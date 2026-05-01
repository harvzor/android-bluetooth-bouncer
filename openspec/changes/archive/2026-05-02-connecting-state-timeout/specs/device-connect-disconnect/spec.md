## MODIFIED Requirements

### Requirement: Connect/Disconnect button visibility rules
The app SHALL show at most one of Connect, Connecting, or Disconnect on a device row at any given time, according to the device's connection and block state. Neither button SHALL be shown when no action is applicable. The "Connecting..." state SHALL take priority over all other rules for the duration of an active connection attempt.

#### Scenario: Blocked device, disconnected — Connect shown
- **WHEN** a device is blocked and not currently connected (and not temp-allowed)
- **AND** no connection attempt is in progress for the device
- **THEN** the Connect button is shown
- **THEN** the Disconnect button is not shown

#### Scenario: Allowed device, disconnected — Connect shown
- **WHEN** a device is allowed and not currently connected
- **AND** no connection attempt is in progress for the device
- **THEN** the Connect button is shown
- **THEN** the Disconnect button is not shown

#### Scenario: Any device, connected — Disconnect shown
- **WHEN** a device is currently connected (whether blocked, temp-allowed, or allowed)
- **THEN** the Disconnect button is shown
- **THEN** the Connect button is not shown

#### Scenario: Temp-allowed device, disconnected — neither shown
- **WHEN** a device is temporarily allowed and not currently connected
- **AND** no connection attempt is in progress for the device
- **THEN** neither Connect nor Disconnect is shown (the device is in a transient auto-reverting state)

#### Scenario: Connection attempt in progress — Connecting shown
- **WHEN** a connection attempt is in progress for a device (regardless of connected or temp-allowed state)
- **THEN** a disabled "Connecting..." label is shown
- **THEN** neither the Connect nor Disconnect button is shown

#### Scenario: Shizuku not ready — buttons disabled
- **WHEN** Shizuku is not ready
- **THEN** Connect and Disconnect buttons are shown but disabled (consistent with Block toggle behaviour)
- **THEN** the "Connecting..." label is not affected by Shizuku state (an in-progress attempt is already past the Shizuku check)
