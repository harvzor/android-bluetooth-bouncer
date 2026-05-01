## Why

The "Watch" toggle label is ambiguous -- users can't tell that it means "stay blocked, but notify me when this device is nearby." The word "watch" could refer to a wristwatch device type or passive observation, and there is no supporting text to clarify. Renaming to "Alert" and adding a confirmation snackbar on enable will make the feature self-explanatory.

## What Changes

- Rename the "Watch" toggle label to "Alert" in the device list UI
- Show a snackbar when the user enables Alert explaining what will happen: the device stays blocked and they'll be notified when it appears in Bluetooth range
- Update all user-facing strings that reference "Watch" (error messages, permission-denied message, notification channel description)
- Internal code names (`isWatched`, `toggleWatch`, `WatchNotificationHelper`, etc.) remain unchanged -- this is a UI label change only

## Capabilities

### New Capabilities

_(none)_

### Modified Capabilities

- `device-watching`: UI label changes from "Watch" to "Alert"; new requirement for a confirmation snackbar when the toggle is enabled

## Impact

- `DeviceListScreen.kt` -- label text, snackbar messages, comments
- `DeviceListViewModel.kt` -- error message string, comments
- `WatchNotificationHelper.kt` -- notification channel description
- No API, database, or dependency changes
