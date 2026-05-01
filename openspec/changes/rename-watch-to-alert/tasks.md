## 1. Rename UI label and user-facing strings

- [ ] 1.1 Rename toggle label from "Watch" to "Alert" in `DeviceListScreen.kt:393`
- [ ] 1.2 Update permission-denied snackbar message at `DeviceListScreen.kt:127` — replace "Watch" with "Alert"
- [ ] 1.3 Update error message at `DeviceListViewModel.kt:271` — "Failed to enable Watch" → "Failed to enable Alert"
- [ ] 1.4 Update notification channel description at `WatchNotificationHelper.kt:46` to remove "watched" wording
- [ ] 1.5 Update code comments referencing "Watch" as the UI label in `DeviceListScreen.kt` and `DeviceListViewModel.kt` (cosmetic)

## 2. Add confirmation snackbar on Alert enable

- [ ] 2.1 Add `watchSuccess: String?` field to `UiState` data class in `DeviceListViewModel.kt`
- [ ] 2.2 Add `onClearWatchSuccess` callback (sets `watchSuccess` to null) and wire it through the Composable parameter list
- [ ] 2.3 Emit `watchSuccess` message in `toggleWatch()` success path after `enableWatch()` completes — message: "When Alert is enabled, you'll get a notification whenever this blocked device appears in Bluetooth range. The device stays blocked -- you'll just be alerted."
- [ ] 2.4 Add `LaunchedEffect(uiState.watchSuccess)` observer in `DeviceListScreen.kt` that shows the snackbar and calls `onClearWatchSuccess`

## 3. Verify

- [ ] 3.1 Build the project (`gradlew.bat assembleDebug`) and confirm no compile errors
