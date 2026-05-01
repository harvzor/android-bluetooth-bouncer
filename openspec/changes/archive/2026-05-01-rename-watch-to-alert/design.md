## Context

The device-watching feature lets users opt blocked Bluetooth devices into background presence monitoring via `CompanionDeviceManager`. The UI toggle for this feature is labelled "Watch" -- a single word with no supporting text. User testing surfaced that the label is ambiguous: it doesn't communicate that the device stays blocked or that the user will receive notifications. The current codebase uses Snackbar messages for error states (permission denied, toggle failure) but provides no positive feedback on successful enable.

## Goals / Non-Goals

**Goals:**
- Rename the user-facing "Watch" label to "Alert" across all touchpoints (toggle label, error messages, notification channel description)
- Show a confirmation Snackbar when Alert is enabled, explaining the full behavior
- Maintain consistency with existing Snackbar patterns in the screen

**Non-Goals:**
- Renaming internal code identifiers (`isWatched`, `toggleWatch`, `WatchNotificationHelper`, `watchError`, etc.) -- the domain concept remains "watch" in code
- Adding tooltips, onboarding flows, or bottom sheets
- Showing a Snackbar when Alert is disabled (self-explanatory)
- Changing any blocking, CDM, or notification behavior

## Decisions

### 1. Use Snackbar, not Toast
**Decision**: Use `SnackbarHostState.showSnackbar()` (already in use on this screen) rather than Android `Toast`.

**Rationale**: The screen already displays Snackbars for permission-denied and toggle-error states via `LaunchedEffect` observers on ViewModel state. Using the same mechanism keeps the feedback pattern consistent. Snackbars are also dismissible and follow Material 3 guidelines.

**Alternative considered**: `Toast.makeText()` -- would work but introduces a second feedback mechanism on the same screen and can't be dismissed.

### 2. Emit confirmation via existing `watchError` state field (renamed semantically)
**Decision**: Add a new `watchSuccess` field to `UiState` (alongside the existing `watchError`) to carry the confirmation message. The UI observes it via `LaunchedEffect` and shows a Snackbar, same pattern as error handling.

**Rationale**: The enable flow is async (CDM association dialog, then `enableWatch()`). The ViewModel already emits `watchError` on failure via `_uiState.update`. A parallel `watchSuccess` field follows the exact same pattern. Using a single Snackbar channel (the same `SnackbarHostState`) means success and error messages naturally don't overlap.

**Alternative considered**: Emitting the Snackbar directly from the Composable's `onToggleWatch` callback -- rejected because the enable flow is async (CDM dialog → callback → enableWatch), so the Composable doesn't know when it actually succeeds.

### 3. Show confirmation on every enable, not just first use
**Decision**: The Snackbar appears every time Alert is toggled on, not just the first time.

**Rationale**: The message is brief enough not to cause fatigue, and users managing multiple devices benefit from consistent reinforcement. The feature is toggled infrequently enough that repetition is not an annoyance.

### 4. Confirmation message wording
**Decision**: Use the message: _"When Alert is enabled, you'll get a notification whenever this blocked device appears in Bluetooth range. The device stays blocked -- you'll just be alerted."_

**Rationale**: Addresses both halves of the user's confusion -- what happens (notification) and what doesn't change (still blocked). Slightly long for a Snackbar (~30 words) but acceptable on modern phone screens (wraps to 3-4 lines).

## Risks / Trade-offs

- **[Long Snackbar text]** The confirmation message may wrap to 3-4 lines on narrow screens. → If it feels too cramped in practice, it can be trimmed to "You'll be notified whenever this blocked device is in Bluetooth range. It stays blocked." No code architecture change needed.
- **[New UiState field]** Adding `watchSuccess` to `UiState` is trivial but adds a field that must be cleared after display. → Same pattern as `watchError` / `toggleError` -- a `LaunchedEffect` observer shows the Snackbar then calls a clear function.
