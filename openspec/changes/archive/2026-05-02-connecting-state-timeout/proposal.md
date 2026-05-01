## Why

After the user taps Connect, the button disappears and no feedback is shown while the OS establishes the Bluetooth connection — leaving the UI in a stranded, unrecoverable state with no indication of progress and no way to retry. Adding a visible "Connecting..." state with a 5-second timeout gives the user clear feedback and automatically recovers when the connection doesn't materialise.

## What Changes

- The Connect button is replaced by a disabled "Connecting..." label while a connection attempt is in progress (Shizuku IPC + OS connection establishment)
- The ViewModel holds `connectLoadingAddress` set until either `isConnected` becomes true or a 5-second timeout elapses — whichever comes first
- On timeout: any policy or temp-allowed changes made during the attempt are fully reverted (policy back to `FORBIDDEN`, `isTemporarilyAllowed` cleared), and the Connect button is restored
- If the Shizuku IPC itself fails (before the wait phase), the error snackbar fires immediately as today — no timeout wait entered

## Capabilities

### New Capabilities

- `connect-pending-state`: The app shows "Connecting..." feedback during the window between IPC completion and OS-level connection confirmation, with automatic timeout and rollback.

### Modified Capabilities

- `device-connect-disconnect`: Button visibility rules gain a new "connecting" state that supersedes the current temp-allowed hiding rule during active attempts. Timeout rollback adds new failure behaviour not currently specified.

## Impact

- `DeviceListViewModel.connectDevice()` — restructured to hold loading state after IPC, wait for `isConnected`, handle timeout with rollback
- `DeviceListScreen.kt` (DeviceRow) — button rendering adds a third branch for `isConnecting` state
- `device-connect-disconnect` spec — new scenarios for connecting state, timeout, and rollback
