# Watch Feature — Debug Notes

## Bug 1: App crashes when tapping Watch toggle

### Symptom
Tapping the Watch toggle immediately crashes the app.

### Attempts

**Crash 1: Missing `uses-feature` declaration**
```
java.lang.IllegalStateException: Must declare uses-feature
android.software.companion_device_setup in manifest to use this API
```
Fix: Added `<uses-feature android:name="android.software.companion_device_setup" />` to `AndroidManifest.xml`.

**Crash 2: Missing `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE` permission**
```
java.lang.SecurityException: [un]registerDevicePresenceListenerService:
Neither user 10384 nor current process has
android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE.
```
Fix: Added `<uses-permission android:name="android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE" />` to `AndroidManifest.xml`. Also wrapped `enableWatch()` call in a try/catch so future exceptions surface as a snackbar instead of crashing.

---

## Bug 2: Watch toggle enables successfully but no notification appears

### Symptom
Watch toggle turns on, no crash, but the "nearby" notification never appears even when the device is in Bluetooth range.

### Investigation

**Finding 1: Stale CDM association IDs**

Multiple crashes during early testing created orphaned CDM associations (IDs 1–10) at the OS level before `enableWatch()` could persist the IDs to Room. The CDM kept delivering `onDeviceAppeared`/`onDeviceDisappeared` callbacks for the oldest stale ID (1), but Room only knew about the latest ID (e.g. 10). Every callback hit:
```
DeviceWatcherService: no entity found for associationId=1
```

Fixes applied:
- `BluetoothBouncerApp.onCreate()`: on API 33+, query `CompanionDeviceManager.myAssociations`, compare against Room, and `disassociate()` any IDs not in Room. This clears orphans on every app start.
- `DeviceWatcherService.resolveEntity()`: when `getDeviceByAssociationId()` returns null, fall back to `getDeviceByMac()` using `AssociationInfo.deviceMacAddress`. If a Room entity is found with a non-null `cdmAssociationId`, use it and heal the stored ID in Room.

After these fixes: `onDeviceAppeared` correctly resolves to the entity and calls `postNearbyNotification()`. Confirmed in logs:
```
DeviceWatcherService: Posted nearby notification for BlackShark V3 X BT
```

**Finding 2: `POST_NOTIFICATIONS` runtime permission never granted**

`dumpsys notification` showed:
```
AppSettings: net.harveywilliams.bluetoothbouncer (10384) importance=NONE
```

The `POST_NOTIFICATIONS` permission (required on API 33+) was declared in the manifest but never requested at runtime. Android silently drops all `notify()` calls without it.

Fix: Added a `POST_NOTIFICATIONS` runtime permission request in `DeviceListScreen`, gated lazily on the Watch toggle. When the user taps Watch enable for the first time:
1. Check if `POST_NOTIFICATIONS` is granted
2. If not, request it via `notificationPermissionLauncher`
3. On grant → proceed with `onToggleWatch(device)`
4. On denial → show snackbar explaining notifications won't work

### Current status
Not yet confirmed working — install with notification permission fix pending test.

---

## Bug 4: Temporarily allowed device shows "Detected" instead of a distinct status

### Symptom
After tapping "Allow temporarily" from the notification, the device appears in the list as "Detected" rather than reflecting its temporarily-allowed state.

### Root cause
`isTemporarilyAllowed` is stored in `BlockedDeviceEntity` (Room) but never flows into `DeviceUiModel`. The row label logic only knows about `isConnected` and `isDetected` — there is no "temporarily allowed" state in the UI model.

### Proposed fix
- Add `isTemporarilyAllowed: Boolean = false` to `DeviceUiModel`
- Populate it in `DeviceListViewModel.refreshDeviceList()` from the `BlockedDeviceEntity`
- In `DeviceRow`, show a "Temporarily allowed" label (e.g. amber/orange colour) when `isTemporarilyAllowed` is true, in place of "Detected"

### Status
Not implemented — noted for a future change.

---

## Bug 3: Step 3 test (device not nearby) never shows error snackbar

### Symptom
When enabling Watch while the device is not in range, the CDM dialog does not time out or fail — it immediately finds the device and shows it in the picker.

### Root cause
`CompanionDeviceManager.associate()` with a `BluetoothDeviceFilter` and `setSingleDevice(true)` uses the Bluetooth bonded device list, not active BT discovery. A bonded device is always "findable" by the stack even when it's not physically in range, so the CDM dialog always succeeds immediately for paired devices. The `onFailure` callback only fires for true errors (e.g. timeout with no bonded device matching), not for "device is not currently nearby."

### Status
This is a CDM platform limitation, not a code bug. The "Device not found nearby" snackbar can only appear if the user cancels the dialog or if `onFailure` fires for a non-bonded device. No fix planned — behavior is acceptable.
