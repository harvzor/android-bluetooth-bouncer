## ADDED Requirements

### Requirement: Track last-detected timestamp per device in memory
The app SHALL maintain an in-memory map from device MAC address to the `elapsedRealtime()` timestamp at which its detection last ended (i.e., its ACL link dropped while it was in the detected state). This map SHALL NOT be persisted to disk. Entries SHALL be automatically evicted after 30 seconds.

#### Scenario: Timestamp stamped when detected device drops ACL
- **WHEN** a device transitions from detected (ACL present, no profile connection) to not-detected (ACL dropped)
- **THEN** its MAC address is added to the last-detected map with the current elapsed realtime
- **THEN** the device row begins showing "Detected Ns ago" on the next UI tick

#### Scenario: Timestamp cleared when device is detected again
- **WHEN** a device that has a last-detected timestamp re-establishes an ACL link (becomes detected again)
- **THEN** its entry is removed from the last-detected map
- **THEN** the device row shows "Detected" (live) instead of "Detected Ns ago"

#### Scenario: Timestamp evicted after 30 seconds
- **WHEN** 30 seconds have elapsed since a device's last-detected timestamp was stamped
- **THEN** its entry is removed from the last-detected map
- **THEN** the device row reverts to showing no detection label

#### Scenario: Map resets on ViewModel destruction
- **WHEN** the ViewModel is cleared (app process killed or ViewModel scope cancelled)
- **THEN** all last-detected timestamps are discarded
- **THEN** no detection history persists across app restarts

### Requirement: Expose seconds-since-last-detection on DeviceUiModel
The `DeviceUiModel` SHALL include a `lastDetectedSecondsAgo: Int?` field. This field SHALL be `null` when there is no active last-detected timestamp for the device (either never detected recently, or the entry has expired). Otherwise it SHALL contain the number of whole seconds elapsed since the timestamp was stamped, in the range 0–29.

#### Scenario: Field is null for device with no recent detection
- **WHEN** a device has no entry in the last-detected map
- **THEN** `lastDetectedSecondsAgo` is `null`

#### Scenario: Field counts up while entry is live
- **WHEN** a device has an active last-detected timestamp
- **THEN** `lastDetectedSecondsAgo` equals `floor((now - timestamp) / 1000)` in seconds
- **THEN** the value increments by 1 each second as the decay ticker fires

### Requirement: Decay ticker updates UI without Bluetooth calls
The app SHALL run a coroutine in `viewModelScope` that ticks once per second while any last-detected timestamp is active. Each tick SHALL remap the existing device list to recompute `lastDetectedSecondsAgo` values and evict expired entries. The ticker SHALL NOT invoke any Bluetooth APIs, Room queries, or reflection. The ticker SHALL self-terminate when the last-detected map is empty.

#### Scenario: Ticker starts when first timestamp is stamped
- **WHEN** a device's last-detected timestamp is stamped for the first time (map was previously empty)
- **THEN** the decay ticker coroutine is launched
- **THEN** `_uiState` is updated once per second with recalculated `lastDetectedSecondsAgo` values

#### Scenario: Ticker stops when all entries expire
- **WHEN** the last entry is evicted from the last-detected map (either by 30-second expiry or device re-detection)
- **THEN** the decay ticker loop exits naturally
- **THEN** no further `_uiState` updates are emitted from the ticker

#### Scenario: Only one ticker runs at a time
- **WHEN** `refreshDeviceList()` is called while a ticker is already running
- **THEN** no additional ticker coroutine is started
