## ADDED Requirements

### Requirement: Device list refreshes when profile proxies become available
When `initProfileProxies()` obtains an A2DP or Headset proxy via `onServiceConnected`, the app SHALL emit a refresh signal so that `refreshDeviceList()` runs with accurate profile-level connection data. The refresh SHALL be subject to the standard 300 ms debounce so that simultaneous A2DP and Headset callbacks coalesce into a single refresh.

#### Scenario: A2DP proxy becomes available
- **WHEN** the A2DP profile proxy `onServiceConnected` callback fires
- **THEN** a refresh signal is emitted and the device list is refreshed within 1 second

#### Scenario: Headset proxy becomes available
- **WHEN** the Headset profile proxy `onServiceConnected` callback fires
- **THEN** a refresh signal is emitted and the device list is refreshed within 1 second

#### Scenario: Both proxies arrive close together
- **WHEN** both A2DP and Headset `onServiceConnected` callbacks fire within 300 ms of each other
- **THEN** only one device list refresh is triggered (debounce coalesces the two signals)

### Requirement: Temporarily-allowed devices are not excluded from ACL-based connection detection
When computing `connectedAddresses`, only devices that are blocked AND NOT temporarily allowed SHALL be excluded from ACL-based connection detection. A temporarily-allowed device with an active ACL link SHALL be included in `connectedAddresses` even when profile proxies have not yet delivered their state.

#### Scenario: Temporarily-allowed device is ACL-connected, profile proxies not yet ready
- **WHEN** a device has `isTemporarilyAllowed = true`, an active ACL connection, and profile proxies are `null`
- **THEN** the device is included in `connectedAddresses`
- **THEN** the device appears in the Connected section with `isConnected = true`

#### Scenario: Blocked (not temporarily-allowed) device has an ACL link
- **WHEN** a device has `isBlocked = true`, `isTemporarilyAllowed = false`, and an active ACL link
- **THEN** the device is NOT included in `connectedAddresses` via ACL
- **THEN** the device appears in the Detected section (unchanged behaviour)

#### Scenario: Temporarily-allowed device connects on cold start
- **WHEN** the app is cold-started after a "Allow temporarily" notification action, and the device has already established an ACL connection before the app is opened
- **THEN** `refreshDeviceList()` on first render shows the device as Connected (not Detected)
- **THEN** once profile proxies deliver their state, the device continues to show as Connected

## MODIFIED Requirements

### Requirement: Temporarily-allowed device connects its profiles
The app SHALL refresh the device list when a temporarily-allowed device's A2DP or HFP profile proxy becomes available, covering both foreground (via Bluetooth broadcast) and cold-start (via proxy `onServiceConnected`) scenarios. After any such refresh, the device SHALL display "Temporarily connected".

#### Scenario: Temporarily-allowed device connects its profiles while app is in foreground
- **WHEN** a temporarily-allowed device connects its A2DP or HFP profiles while the app is in the foreground (over an existing ACL link, triggering a profile connection broadcast)
- **THEN** the device list refreshes within 1 second
- **THEN** the device displays "Temporarily connected"

#### Scenario: Temporarily-allowed device is already profile-connected when app cold-starts
- **WHEN** the app opens cold after a "Allow temporarily" notification action and the device has already established its A2DP or HFP profiles
- **THEN** `refreshDeviceList()` on first render shows the device as "Temporarily connected"
