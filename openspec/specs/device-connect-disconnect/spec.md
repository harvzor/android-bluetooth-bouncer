### Requirement: Connect a blocked device temporarily
The app SHALL provide a Connect button for each blocked and disconnected device on API 33 and above. Tapping Connect SHALL set `CONNECTION_POLICY_ALLOWED` on all supported profiles via Shizuku, mark the device as temporarily allowed in the database, and ensure a CDM association exists (creating one with a system confirmation dialog if not already present) so that `DeviceWatcherService` can revert the policy when the device leaves range. The Connect button SHALL NOT be shown on API < 33.

#### Scenario: Connect on a blocked disconnected device (first time, no CDM association)
- **WHEN** the user taps Connect on a blocked disconnected device on API 33+
- **AND** no CDM association exists for the device (`cdmAssociationId` is null)
- **THEN** the system confirmation dialog is shown
- **WHEN** the user confirms the dialog
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to true in `BlockedDeviceEntity`
- **THEN** `cdmAssociationId` is persisted and `startObservingDevicePresence()` is called

#### Scenario: Connect on a blocked disconnected device (CDM association already exists)
- **WHEN** the user taps Connect on a blocked disconnected device on API 33+
- **AND** a CDM association already exists (`cdmAssociationId` is non-null)
- **THEN** no system confirmation dialog is shown
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on all supported profiles via Shizuku
- **THEN** `isTemporarilyAllowed` is set to true in `BlockedDeviceEntity`
- **THEN** `startObservingDevicePresence()` is called with the existing association ID

#### Scenario: Connect fails because Shizuku is unavailable
- **WHEN** the user taps Connect on a blocked disconnected device
- **AND** Shizuku is not running or permission is denied
- **THEN** the app displays an error snackbar
- **THEN** `isTemporarilyAllowed` remains false
- **THEN** no CDM association is created or modified

#### Scenario: User cancels the CDM confirmation dialog during Connect
- **WHEN** the user taps Connect on a blocked disconnected device
- **AND** the system confirmation dialog is shown
- **AND** the user dismisses the dialog
- **THEN** no policy change is made
- **THEN** `isTemporarilyAllowed` remains false

#### Scenario: Connect button hidden on API < 33
- **WHEN** the device is running Android 12 (API 31 or 32)
- **THEN** the Connect button is not shown for any device

### Requirement: Connect an allowed device
The app SHALL provide a Connect button for each allowed and disconnected device on API 33 and above. Tapping Connect SHALL call `connectDevice` on the Shizuku UserService to initiate active profile connections. No policy change SHALL be made. If Android subsequently auto-connects the device due to `POLICY_ALLOWED`, this is expected behaviour and requires no additional handling.

#### Scenario: Connect on an allowed disconnected device
- **WHEN** the user taps Connect on an allowed disconnected device on API 33+
- **THEN** the app calls `connectDevice(macAddress)` via the Shizuku UserService
- **THEN** the app attempts to connect A2DP, Headset, and HID Host profiles
- **THEN** no policy change is made to `BlockedDeviceEntity`

#### Scenario: Connect fails on an allowed device
- **WHEN** the user taps Connect on an allowed disconnected device
- **AND** Shizuku is not running or the connection attempt fails on all profiles
- **THEN** the app displays an error snackbar

### Requirement: Disconnect a connected device
The app SHALL provide a Disconnect button for each connected device on API 33 and above. The button SHALL appear for blocked, temp-allowed, and allowed devices when connected. Tapping Disconnect SHALL call `disconnectDevice` on the Shizuku UserService. For blocked and temp-allowed devices, Disconnect SHALL also re-apply `CONNECTION_POLICY_FORBIDDEN` and clear `isTemporarilyAllowed`. For allowed devices, no policy change SHALL be made (Android may auto-reconnect; this is accepted behaviour).

#### Scenario: Disconnect a temporarily-allowed connected device
- **WHEN** the user taps Disconnect on a device that is connected and temporarily allowed
- **THEN** the app calls `disconnectDevice(macAddress)` via the Shizuku UserService
- **THEN** the app calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on all supported profiles
- **THEN** `isTemporarilyAllowed` is set to false in `BlockedDeviceEntity`

#### Scenario: Disconnect a blocked connected device (race condition / lingering ACL)
- **WHEN** the user taps Disconnect on a device that is connected and blocked
- **THEN** the app calls `disconnectDevice(macAddress)` via the Shizuku UserService
- **THEN** `CONNECTION_POLICY_FORBIDDEN` is re-applied on all supported profiles
- **THEN** `isTemporarilyAllowed` is not changed (it was already false)

#### Scenario: Disconnect an allowed connected device
- **WHEN** the user taps Disconnect on a device that is connected and allowed
- **THEN** the app calls `disconnectDevice(macAddress)` via the Shizuku UserService
- **THEN** no policy change is made
- **THEN** Android may auto-reconnect the device (accepted; no further action)

#### Scenario: Disconnect fails
- **WHEN** the user taps Disconnect on a connected device
- **AND** Shizuku is not running or the disconnect attempt fails
- **THEN** the app displays an error snackbar
- **THEN** no policy change is made

### Requirement: Connect/Disconnect button visibility rules
The app SHALL show at most one of Connect or Disconnect on a device row at any given time, according to the device's connection and block state. Neither button SHALL be shown when no action is applicable.

#### Scenario: Blocked device, disconnected — Connect shown
- **WHEN** a device is blocked and not currently connected (and not temp-allowed)
- **THEN** the Connect button is shown
- **THEN** the Disconnect button is not shown

#### Scenario: Allowed device, disconnected — Connect shown
- **WHEN** a device is allowed and not currently connected
- **THEN** the Connect button is shown
- **THEN** the Disconnect button is not shown

#### Scenario: Any device, connected — Disconnect shown
- **WHEN** a device is currently connected (whether blocked, temp-allowed, or allowed)
- **THEN** the Disconnect button is shown
- **THEN** the Connect button is not shown

#### Scenario: Temp-allowed device, disconnected — neither shown
- **WHEN** a device is temporarily allowed and not currently connected
- **THEN** neither Connect nor Disconnect is shown (the device is in a transient auto-reverting state)

#### Scenario: Shizuku not ready — buttons disabled
- **WHEN** Shizuku is not ready
- **THEN** Connect and Disconnect buttons are shown but disabled (consistent with Block toggle behaviour)
