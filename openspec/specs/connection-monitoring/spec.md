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

#### Scenario: Temporarily-allowed device connects its profiles
- **WHEN** a temporarily-allowed device connects its A2DP or HFP profiles while the app is in the foreground (over an existing ACL link, without a new ACL_CONNECTED event)
- **THEN** the device list refreshes within 1 second
- **THEN** the device displays "Temporarily connected"

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
