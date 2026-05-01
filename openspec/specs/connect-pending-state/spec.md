### Requirement: Show connecting feedback during active connection attempt
The app SHALL display a disabled "Connecting..." label in place of the Connect button for the duration of an active connection attempt. The connecting state SHALL begin when the user taps Connect and SHALL persist until either the device's `isConnected` state becomes true or a 5-second timeout elapses. The connecting state SHALL take priority over all other button-visibility rules (including the temp-allowed hiding rule) for the affected device.

#### Scenario: Connecting label shown immediately after tap
- **WHEN** the user taps Connect on a device
- **THEN** the Connect button is replaced by a disabled "Connecting..." label immediately

#### Scenario: Connecting label dismissed on successful connection
- **WHEN** the device's `isConnected` state becomes true within 5 seconds of the connect attempt
- **THEN** the "Connecting..." label is replaced by the Disconnect button

#### Scenario: Connecting label dismissed on IPC failure
- **WHEN** the Shizuku IPC call returns a failure result
- **THEN** the "Connecting..." label is replaced by the Connect button
- **THEN** an error snackbar is shown
- **THEN** no 5-second wait is entered

### Requirement: Timeout and rollback on failed connection
The app SHALL revert all side effects of a Connect attempt if the device does not reach `isConnected = true` within 5 seconds of a successful Shizuku IPC call. Reversion SHALL include restoring `CONNECTION_POLICY_FORBIDDEN` (for blocked devices) and clearing `isTemporarilyAllowed` (if set). The Connect button SHALL be restored after rollback.

#### Scenario: Timeout on blocked device connect attempt
- **WHEN** the user taps Connect on a blocked device
- **AND** the Shizuku IPC succeeds (policy set to `ALLOWED`, `isTemporarilyAllowed` set to true)
- **AND** 5 seconds elapse without `isConnected` becoming true
- **THEN** `setConnectionPolicy(POLICY_FORBIDDEN)` is called via Shizuku
- **THEN** `isTemporarilyAllowed` is cleared to false in the database
- **THEN** the "Connecting..." label is replaced by the Connect button
- **THEN** a "Connection timed out" error snackbar is shown

#### Scenario: Timeout on allowed device connect attempt
- **WHEN** the user taps Connect on an allowed device
- **AND** the Shizuku IPC succeeds
- **AND** 5 seconds elapse without `isConnected` becoming true
- **THEN** the "Connecting..." label is replaced by the Connect button
- **THEN** a "Connection timed out" error snackbar is shown
- **THEN** no policy change is made (allowed device has no policy to revert)
