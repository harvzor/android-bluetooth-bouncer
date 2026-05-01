### Requirement: Detect Shizuku availability
The app SHALL check whether Shizuku is installed and running when the app launches. The app SHALL display the current Shizuku status to the user (not installed, installed but not running, running and ready).

#### Scenario: Shizuku is running
- **WHEN** the user opens the app and Shizuku is installed and running
- **THEN** the app displays a "Shizuku: Ready" status indicator
- **THEN** all device blocking/unblocking operations are available

#### Scenario: Shizuku is installed but not running
- **WHEN** the user opens the app and Shizuku is installed but not started
- **THEN** the app displays a "Shizuku: Not running" status with instructions to start it

#### Scenario: Shizuku is not installed
- **WHEN** the user opens the app and Shizuku is not installed
- **THEN** the app displays a setup screen with a link to install Shizuku from the Play Store or GitHub

### Requirement: Request Shizuku permission
The app SHALL request Shizuku permission before executing any privileged operations. The permission request SHALL use Shizuku's standard permission flow. The app SHALL handle both grant and denial.

#### Scenario: Shizuku permission granted
- **WHEN** the app requests Shizuku permission and the user grants it
- **THEN** the app proceeds with privileged Bluetooth operations

#### Scenario: Shizuku permission denied
- **WHEN** the app requests Shizuku permission and the user denies it
- **THEN** the app displays a message explaining that Shizuku permission is required for device blocking
- **THEN** device blocking toggles are disabled

### Requirement: Bind to Shizuku UserService
The app SHALL bind to a Shizuku UserService that runs as the shell UID. The UserService SHALL expose an AIDL interface for setting Bluetooth connection policies. The app SHALL handle service connection and disconnection lifecycle events. The app SHALL begin binding the UserService as early as possible in the application lifecycle (at `Application.onCreate()`).

#### Scenario: UserService binds successfully
- **WHEN** Shizuku is running and permission is granted
- **AND** the app requests to bind the UserService
- **THEN** the UserService starts as the shell UID
- **THEN** the app receives a binder to the AIDL interface

#### Scenario: UserService disconnects
- **WHEN** the Shizuku UserService disconnects (e.g., Shizuku stops)
- **THEN** the app updates its status to reflect Shizuku is unavailable
- **THEN** blocking/unblocking operations report an error if attempted

### Requirement: Execute setConnectionPolicy via UserService
The UserService SHALL accept requests to set the connection policy for a given Bluetooth device and profile. The UserService SHALL call `BluetoothProfile.setConnectionPolicy(device, policy)` for each requested profile. The UserService SHALL return success or failure for each profile operation. If `setConnectionPolicy` is called while the UserService is still binding (Shizuku is running and permission is granted but the binder has not yet been delivered), the call SHALL wait up to 10 seconds for the UserService to become available before failing.

#### Scenario: Set policy to FORBIDDEN on all profiles
- **WHEN** the app requests the UserService to block a device
- **THEN** the UserService obtains profile proxies for A2DP, Headset, and HID Host
- **THEN** the UserService calls `setConnectionPolicy(device, CONNECTION_POLICY_FORBIDDEN)` on each profile
- **THEN** the UserService returns success for each profile that was updated

#### Scenario: Set policy to ALLOWED on all profiles
- **WHEN** the app requests the UserService to unblock a device
- **THEN** the UserService calls `setConnectionPolicy(device, CONNECTION_POLICY_ALLOWED)` on each profile
- **THEN** the UserService returns success for each profile that was updated

#### Scenario: Profile proxy unavailable
- **WHEN** the UserService cannot obtain a profile proxy (e.g., profile not supported on device)
- **THEN** the UserService skips that profile and returns a partial success result indicating which profiles were updated

#### Scenario: setConnectionPolicy called during UserService bind (cold start)
- **WHEN** the app process was not running and is woken by a background event
- **AND** `setConnectionPolicy` is called before the Shizuku UserService binder has been delivered
- **AND** Shizuku is installed, running, and permission is granted
- **THEN** the call suspends and waits for the UserService to become available
- **THEN** once available, the call proceeds and returns a result normally

#### Scenario: setConnectionPolicy times out waiting for UserService
- **WHEN** `setConnectionPolicy` is waiting for the UserService to become available
- **AND** the UserService does not bind within 10 seconds
- **THEN** the call returns `Result.failure`
- **THEN** an error notification is shown with the text "Could not allow — Shizuku unavailable"

### Requirement: Guide user through Shizuku setup
The app SHALL provide a setup screen that guides users through installing and starting Shizuku. The screen SHALL include step-by-step instructions for both ADB and Wireless Debugging methods.

#### Scenario: User views setup instructions
- **WHEN** the user navigates to the Shizuku setup screen
- **THEN** the app displays instructions for installing Shizuku
- **THEN** the app displays instructions for starting Shizuku via ADB or Wireless Debugging
- **THEN** the app provides a link to install Shizuku (Play Store or GitHub)

#### Scenario: Shizuku becomes available during setup
- **WHEN** the user is on the setup screen and starts Shizuku externally
- **THEN** the app detects Shizuku is now running and updates the status indicator
- **THEN** the user can navigate to the device list

### Requirement: Connect a Bluetooth device via UserService
The Shizuku UserService SHALL expose a `connectDevice(macAddress: String): IntArray` method on its AIDL interface. The UserService SHALL obtain profile proxies for A2DP, Headset, and HID Host and call the hidden `connect(device)` method on each via reflection. The return value SHALL follow the existing convention: one entry per profile — `1` = success, `0` = returned false, `-1` = proxy unavailable. The app SHALL treat the call as a failure if no profile entry equals `1`.

#### Scenario: Connect succeeds on at least one profile
- **WHEN** the app calls `connectDevice(macAddress)` via the UserService
- **AND** at least one profile proxy's `connect(device)` returns true
- **THEN** the UserService returns an IntArray with at least one entry equal to `1`
- **THEN** the app treats the call as a success

#### Scenario: Connect fails on all profiles
- **WHEN** the app calls `connectDevice(macAddress)` via the UserService
- **AND** all profile proxies return false or are unavailable
- **THEN** the UserService returns an IntArray with no entry equal to `1`
- **THEN** the app treats the call as a failure and shows an error snackbar

#### Scenario: Profile proxy unavailable during connect
- **WHEN** the UserService cannot obtain a profile proxy for a given profile
- **THEN** the UserService records `-1` for that profile and continues with the remaining profiles

### Requirement: Disconnect a Bluetooth device via UserService
The Shizuku UserService SHALL expose a `disconnectDevice(macAddress: String): IntArray` method on its AIDL interface. The UserService SHALL obtain profile proxies for A2DP, Headset, and HID Host and call the hidden `disconnect(device)` method on each via reflection. The return value SHALL follow the same convention as `connectDevice`. The app SHALL treat the call as a failure if no profile entry equals `1`.

#### Scenario: Disconnect succeeds on at least one profile
- **WHEN** the app calls `disconnectDevice(macAddress)` via the UserService
- **AND** at least one profile proxy's `disconnect(device)` returns true
- **THEN** the UserService returns an IntArray with at least one entry equal to `1`
- **THEN** the app treats the call as a success

#### Scenario: Disconnect fails on all profiles
- **WHEN** the app calls `disconnectDevice(macAddress)` via the UserService
- **AND** all profile proxies return false or are unavailable
- **THEN** the UserService returns an IntArray with no entry equal to `1`
- **THEN** the app treats the call as a failure and shows an error snackbar

#### Scenario: Profile proxy unavailable during disconnect
- **WHEN** the UserService cannot obtain a profile proxy for a given profile
- **THEN** the UserService records `-1` for that profile and continues with the remaining profiles
