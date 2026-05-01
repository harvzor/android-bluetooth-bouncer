## Why

The device list is a flat sorted list where devices jump position unexpectedly — most jarring when a device transitions from "Detected" to "Detected recently", causing it to drop instantly from the detected group to its alphabetical position. Section headers make groupings explicit and eliminate confusion about why items move.

## What Changes

- The flat device list is replaced with a sectioned list: **Connected**, **Detected**, **Blocked**, **Allowed**
- Sections with no devices are hidden entirely
- A device's section is determined by its most-active state: Connected > Detected (including recently detected) > Blocked > Allowed
- Devices with a non-null `lastDetectedSecondsAgo` remain in the **Detected** section for the full 30-second decay window — they no longer drop to alphabetical immediately
- The "Detected Xs ago" per-second countdown label is replaced with a static "Detected recently" label
- Within each section, devices are sorted alphabetically by name
- Within the Detected section, live-detected devices sort above recently-detected devices

## Capabilities

### New Capabilities
- `device-list-sections`: Section headers grouping devices by state (Connected / Detected / Blocked / Allowed), with empty sections hidden

### Modified Capabilities
- `device-list-visual-states`: The "Detected Xs ago" status label is replaced by "Detected recently"
- `detection-decay`: Sort order must treat `lastDetectedSecondsAgo != null` as equivalent to `isDetected = true` for section placement — recently-detected devices stay in the Detected section until decay expires

## Impact

- `DeviceListViewModel.kt`: Sort comparator updated; `DeviceUiModel` grouping logic added
- `DeviceListScreen.kt`: `LazyColumn` restructured to render section headers; item rendering updated for new label
- `device-list-visual-states` spec: "Detected Xs ago" label requirement updated
- `detection-decay` spec: Sort-tier requirement added for decaying devices
