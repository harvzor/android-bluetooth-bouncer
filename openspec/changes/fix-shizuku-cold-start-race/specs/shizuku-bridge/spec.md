## MODIFIED Requirements

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
