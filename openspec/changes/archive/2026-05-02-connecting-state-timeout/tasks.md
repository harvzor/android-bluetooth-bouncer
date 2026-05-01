## 1. ViewModel — extend connecting state lifetime

- [x] 1.1 Extract the post-IPC success path in `connectDevice` (both blocked and allowed branches) into a shared `awaitConnectionOrTimeout(address)` suspend function that holds `connectLoadingAddress` set, observes `_uiState` for `isConnected`, and fires rollback on `TimeoutCancellationException`
- [x] 1.2 Implement rollback logic in the timeout handler: call `shizukuHelper.setConnectionPolicy(POLICY_FORBIDDEN)` and `blockedDeviceDao.updateIsTemporarilyAllowed(address, false)` only when the device was a blocked/temp-allowed connect attempt
- [x] 1.3 On timeout, set `toggleError` to a "Connection timed out" message before clearing `connectLoadingAddress`
- [x] 1.4 Ensure IPC failure (existing `Result.failure` path) still clears `connectLoadingAddress` immediately without entering the timeout wait

## 2. UI — add Connecting branch to DeviceRow

- [x] 2.1 In `DeviceListScreen.kt`, add a `isConnecting` branch as the first case in the `when` block inside `DeviceRow` — show a disabled `TextButton` with label "Connecting..." when `isConnectLoading` is true
- [x] 2.2 Verify the `isConnecting` branch takes priority over the `showConnect`/`showDisconnect` cases (ordering in `when` ensures this)

## 3. Verification

- [x] 3.1 Test on a physical device: tap Connect on a blocked device, confirm "Connecting..." appears, confirm Disconnect appears once connected
- [x] 3.2 Test timeout path: tap Connect while device is out of range, confirm "Connecting..." shows for ~5 seconds, Connect button restores, error snackbar appears
- [x] 3.3 Test Shizuku IPC failure path: confirm error snackbar fires immediately with no 5-second wait
- [x] 3.4 Test allowed device connect: same happy path and timeout behaviour as blocked device
