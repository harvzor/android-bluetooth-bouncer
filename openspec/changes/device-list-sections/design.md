## Context

The device list is a flat `LazyColumn` in `DeviceListScreen.kt`, driven by a `List<DeviceUiModel>` sorted in `DeviceListViewModel.kt`. The current sort is: connected first, then detected, then alphabetical. There are no visual section boundaries.

The core problem is that "recently detected" devices (`lastDetectedSecondsAgo != null`) have `isDetected = false`, so the sort immediately places them in the alphabetical group — causing a jarring jump the instant detection is lost.

## Goals / Non-Goals

**Goals:**
- Divide the list into four named sections: Connected, Detected, Blocked, Allowed
- Hide empty sections entirely
- Keep recently-detected devices in the Detected section for their full 30s decay window
- Replace the per-second "Detected Xs ago" label with a static "Detected recently"

**Non-Goals:**
- Collapsible sections
- Per-section item counts
- Changing the decay window (remains 30 seconds)
- Animating section header appearance/disappearance

## Decisions

### Decision 1: Add `section: DeviceSection` field to `DeviceUiModel`

A `DeviceSection` enum (`CONNECTED`, `DETECTED`, `BLOCKED`, `ALLOWED`) is computed in the ViewModel alongside the existing fields, and stored directly on `DeviceUiModel`. The section assignment logic mirrors the proposal:

```
isConnected                          → CONNECTED
isDetected || lastDetectedSecondsAgo != null  → DETECTED
isBlocked                            → BLOCKED
else                                 → ALLOWED
```

**Why not derive section in the UI?** The ViewModel is already the single source of truth for device state. Putting section logic in the composable duplicates the decision about what "active state wins" and makes it harder to test or adjust.

**Why not a sealed `DeviceListEntry` class?** A sealed class (SectionHeader | DeviceItem) would require changing the type passed to the composable and adds indirection without meaningful benefit at this scale. A `section` field on the existing model is the minimal change.

### Decision 2: Sort by section ordinal first, then alphabetical within section; live-detected before recently-detected within Detected

```kotlin
compareBy<DeviceUiModel> { it.section.ordinal }
    .thenByDescending { it.isDetected }          // live before "recently"
    .thenBy { it.name }
```

The `DeviceSection` enum ordinal defines display order (CONNECTED=0, DETECTED=1, BLOCKED=2, ALLOWED=3).

### Decision 3: Section headers rendered in the composable by detecting section transitions

The `DeviceList` composable iterates the sorted list and emits a `SectionHeader` item before the first device in each new section. No structural change to the `LazyColumn` items type or state shape is needed — the header is keyed by `"header_${section.name}"` for stable animations.

```
items loop:
  if device.section != previousSection → emit header item
  emit device item
```

### Decision 4: Keep the decay ticker as-is

The ticker still fires every second to (a) increment `lastDetectedSecondsAgo` for eviction purposes and (b) evict entries at 30s. The label change ("Detected recently") is purely in the composable — no ViewModel change needed for the label.

## Risks / Trade-offs

- **Section header flicker on first detection**: When a device first appears in the Detected section, a header may appear/disappear if no other device was in that section. The `key`-based `LazyColumn` will animate this, which should feel natural. Risk: low.
- **"Detected recently" loses precision**: Users who want to know exactly how stale the detection is lose the second-by-second counter. Acceptable given that section membership already communicates "this was very recently nearby."
- **`isBlocked` was not previously a sort factor**: Moving blocked devices into their own section may surprise users who had a mental model of the old order. The section header makes the grouping self-explanatory.
