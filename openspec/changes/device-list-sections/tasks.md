## 1. ViewModel — Section assignment

- [ ] 1.1 Add `DeviceSection` enum (`CONNECTED`, `DETECTED`, `BLOCKED`, `ALLOWED`) to `DeviceListViewModel.kt`
- [ ] 1.2 Add `section: DeviceSection` field to `DeviceUiModel`
- [ ] 1.3 Compute `section` in `refreshDeviceList()` using the priority rule: Connected > (isDetected || lastDetectedSecondsAgo != null) > Blocked > Allowed

## 2. ViewModel — Sort order

- [ ] 2.1 Replace the existing `sortedWith` comparator with one that sorts by `section.ordinal` first, then `isDetected` descending (live before recently-detected), then name alphabetically
- [ ] 2.2 Verify the decay ticker path also has the correct `section` value — since the ticker remaps existing `DeviceUiModel` entries via `device.copy(lastDetectedSecondsAgo = ...)`, confirm `section` is recomputed (or extracted to a helper) so a decaying device keeps `DETECTED` section assignment during ticks

## 3. UI — Section headers

- [ ] 3.1 Add a `SectionHeader` composable in `DeviceListScreen.kt` that renders a section title (small uppercase label, `onSurfaceVariant` color, horizontal padding)
- [ ] 3.2 Refactor `DeviceList` to iterate the sorted device list and emit a `SectionHeader` item (keyed `"header_${section.name}"`) before the first device in each new section
- [ ] 3.3 Remove the `HorizontalDivider` between the last item of one section and the section header of the next — the header provides visual separation

## 4. UI — Label change

- [ ] 4.1 In `DeviceRow`, replace `"Detected ${device.lastDetectedSecondsAgo}s ago"` with `"Detected recently"`

## 5. Verification

- [ ] 5.1 Build the project and confirm no compile errors
- [ ] 5.2 On device: connect a Bluetooth device, confirm it appears under Connected section; disconnect, confirm it moves to Detected (if detected) or Allowed/Blocked
- [ ] 5.3 On device: trigger a detected→not-detected transition for a blocked device; confirm it shows "Detected recently" and stays in Detected section for ~30s, then moves to Blocked
- [ ] 5.4 Confirm sections with no devices are not rendered (no orphaned headers)
