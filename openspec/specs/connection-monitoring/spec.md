### Requirement: Observe Bluetooth ACL connection events
The app SHALL register a BroadcastReceiver for `ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`, and `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` while the device list screen is active. When any of these events is received, the app SHALL refresh the device list so that connection indicators AND detection indicators reflect current state.

#### Scenario: Device connects while app is open
- **WHEN** a paired Bluetooth device establishes an ACL connection while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the newly connected device displays the "Connected" indicator

#### Scenario: Device disconnects while app is open
- **WHEN** a connected Bluetooth device drops its ACL connection while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the disconnected device no longer displays the "Connected" indicator
- **THEN** if the device retains an ACL link (e.g. it is blocked), it displays the "Detected" indicator instead

#### Scenario: Blocked device comes into ACL range
- **WHEN** a blocked paired device establishes an ACL link while the app is in the foreground
- **THEN** the device list refreshes within 1 second
- **THEN** the device displays the "Detected" indicator
- **THEN** the device does NOT display the "Connected" indicator

### Requirement: Temporarily-allowed device connects its profiles
The app SHALL refresh the device list when a temporarily-allowed device's A2DP or HFP profile proxy becomes available, covering both foreground (via Bluetooth broadcast) and cold-start (via proxy `onServiceConnected`) scenarios. After any such refresh, the device SHALL display "Temporarily connected".

#### Scenario: Temporarily-allowed device connects its profiles while app is in foreground
- **WHEN** a temporarily-allowed device connects its A2DP or HFP profiles while the app is in the foreground (over an existing ACL link, triggering a profile connection broadcast)
- **THEN** the device list refreshes within 1 second
- **THEN** the device displays "Temporarily connected"

#### Scenario: Temporarily-allowed device is already profile-connected when app cold-starts
- **WHEN** the app opens cold after a "Allow temporarily" notification action and the device has already established its A2DP or HFP profiles
- **THEN** `refreshDeviceList()` on first render shows the device as "Temporarily connected"

### Requirement: Device list refreshes when profile proxies become available
The app SHALL trigger a device list refresh whenever a Bluetooth profile proxy (A2DP or Headset) becomes available via `onServiceConnected`, so that devices which connected before the app opened are reflected with accurate profile-connection state.

#### Scenario: A2DP proxy becomes available
- **WHEN** the A2DP profile proxy's `onServiceConnected` fires after the device list screen is active
- **THEN** the device list refreshes to reflect current A2DP connection state

#### Scenario: Headset proxy becomes available
- **WHEN** the Headset profile proxy's `onServiceConnected` fires after the device list screen is active
- **THEN** the device list refreshes to reflect current HFP connection state

#### Scenario: Both proxies arrive close together (debounce)
- **WHEN** both the A2DP and Headset proxies' `onServiceConnected` callbacks fire within a short window
- **THEN** the app debounces the refreshes and updates the device list once rather than twice

### Requirement: Temporarily-allowed devices are not excluded from ACL-based connection detection
The app SHALL include temporarily-allowed devices when evaluating ACL connections, so that an ACL link on a temporarily-allowed device is detected and displayed correctly regardless of whether profile proxies have been resolved.

#### Scenario: Temporarily-allowed device has an ACL link but profile proxies are null
- **WHEN** a temporarily-allowed device has an active ACL link and the A2DP/HFP profile proxies have not yet connected
- **THEN** the device is NOT excluded from ACL detection and its ACL connection is still surfaced in the device list

#### Scenario: Blocked (non-temp-allowed) device has an ACL link
- **WHEN** a blocked device (not temporarily allowed) has an active ACL link
- **THEN** the device displays the "Detected" indicator, consistent with existing blocked-device behaviour

#### Scenario: Cold-start temp-allowed device
- **WHEN** the app opens cold, a temporarily-allowed device has an ACL link, and profile proxies have not yet resolved
- **THEN** the device is detected via ACL and displayed in the device list without being filtered out

#### Scenario: Multiple devices change state rapidly
- **WHEN** multiple Bluetooth connection events fire within a short time window (e.g., Bluetooth toggled off)
- **THEN** the app debounces the events and refreshes the device list once rather than per-event

### Requirement: Observe Bluetooth adapter state changes
The app SHALL register a BroadcastReceiver for `BluetoothAdapter.ACTION_STATE_CHANGED` while the device list screen is active. When Bluetooth is turned off, the device list SHALL clear. When Bluetooth is turned on, the device list SHALL repopulate.

#### Scenario: Bluetooth turned off while app is open
- **WHEN** the user disables Bluetooth while the app is in the foreground
- **THEN** the device list clears and shows a "Bluetooth disabled" state

#### Scenario: Bluetooth turned on while app is open
- **WHEN** the user enables Bluetooth while the app is in the foreground
- **THEN** the device list repopulates with current paired devices and their connection status

### Requirement: Receiver lifecycle management
The BroadcastReceiver for connection and adapter events SHALL only be registered while the ViewModel is alive. The receiver SHALL be unregistered in the ViewModel's `onCleared()` callback. The receiver SHALL NOT be registered until `BLUETOOTH_CONNECT` permission has been granted.

#### Scenario: App is closed
- **WHEN** the user navigates away and the ViewModel is cleared
- **THEN** the BroadcastReceiver is unregistered and no longer consumes events

#### Scenario: Permission not yet granted
- **WHEN** the ViewModel initialises but `BLUETOOTH_CONNECT` permission has not been granted
- **THEN** the BroadcastReceiver is NOT registered
- **WHEN** the permission is subsequently granted
- **THEN** the BroadcastReceiver is registered
