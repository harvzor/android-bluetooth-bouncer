## Context

`ShizukuHelper.setConnectionPolicy()` is a `suspend` function that checks `userService != null` before invoking the AIDL method. On a cold start (app process not running), `shizukuHelper` is a `by lazy` property — it isn't initialized until first access. When the OS wakes the app for a CDM presence event and the user immediately taps "Allow temporarily", `shizukuHelper` is initialized for the first time inside `TemporaryAllowReceiver`. The `ShizukuHelper` init block calls `refreshState()` → `bindUserService()`, which is asynchronous. By the time `setConnectionPolicy()` is called milliseconds later, `userService` is still null and the call fails immediately.

All four background callers (`TemporaryAllowReceiver`, `DeviceWatcherService`, `BondStateReceiver`, `BootReceiver`) are vulnerable — two (`BondStateReceiver`, `BootReceiver`) already check `state.value` before calling, but they see `NotRunning` or `PermissionDenied` during the bind window and silently bail out rather than waiting. The fix belongs inside `setConnectionPolicy()` so all callers are covered without changes.

## Goals / Non-Goals

**Goals:**
- Eliminate the false "Shizuku unavailable" failure that occurs when `setConnectionPolicy()` is called during the UserService bind window.
- Start the Shizuku binding process as early as possible in the app lifecycle.
- All existing callers work correctly without modification.

**Non-Goals:**
- Retry logic for when Shizuku is genuinely not running or not installed — that remains an immediate failure.
- Changes to `BondStateReceiver` or `BootReceiver` pre-checks (those can be simplified in a later cleanup, but are not wrong today).
- Any change to the Shizuku UserService itself or its AIDL interface.

## Decisions

### 1. Await `State.Ready` inside `setConnectionPolicy()`, not at each call site

**Decision:** Add the wait logic once inside `ShizukuHelper.setConnectionPolicy()`.

**Rationale:** There are five call sites across four files. Fixing each individually is error-prone and creates inconsistency. `setConnectionPolicy()` is the natural chokepoint — all callers already treat it as a suspending function and handle `Result.failure`.

**Alternative considered:** Add a `awaitReady(timeout)` helper and call it from each receiver.  
Rejected — spreads responsibility across call sites and requires changing every caller.

---

### 2. Wait only when Shizuku is running and permitted

**Decision:** Only enter the wait path when `isShizukuRunning() && hasPermission()` — i.e., when the UserService bind is legitimately in progress. If Shizuku is not installed, not running, or permission is denied, fail immediately (same as today).

**Rationale:** Avoids unnecessary 10-second hang when Shizuku is genuinely unavailable. The heuristic is sound: if Shizuku is running and we have permission, `userService` being null means binding is in progress.

---

### 3. Use `_state.first { it is State.Ready }` with `withTimeout`

**Decision:** Suspend on the existing `_state` `StateFlow` using `first { it is State.Ready }` wrapped in `withTimeout(10_000)`.

**Rationale:** `_state` already reflects the exact lifecycle transition we need. No additional channels or polling required. `withTimeout` provides a safe upper bound and throws `TimeoutCancellationException`, which is caught by the existing `catch (e: Exception)` block in `setConnectionPolicy()` — keeping the failure path identical to today.

**Alternative considered:** `delay`-based polling loop.  
Rejected — busy-waits are wasteful and fragile.

---

### 4. Eager initialization of `shizukuHelper` in `Application.onCreate()`

**Decision:** Access `shizukuHelper` during `BluetoothBouncerApp.onCreate()` to trigger the `by lazy` init and start the Shizuku bind immediately.

**Rationale:** Narrows the race window by ~100–500ms in practice. The await in `setConnectionPolicy()` is the primary fix; eager init is belt-and-suspenders that also helps `DeviceWatcherService` (which can call `setConnectionPolicy()` before any user interaction).

**Alternative considered:** Change `by lazy` to a concrete field initialized at declaration.  
Equivalent effect; accessing the lazy in `onCreate()` is a one-line change that preserves the existing architecture.

---

### 5. Error notification text: "Could not allow — Shizuku unavailable"

**Decision:** Replace the existing string "Could not allow — Shizuku is not running" with "Could not allow — Shizuku unavailable".

**Rationale:** With the await fix, the only scenario that reaches the error notification is when Shizuku truly cannot be used (not installed, not running, permission denied, or 10-second timeout). "Not running" was factually wrong in the race condition case. The new text is accurate for all failure modes without needing per-case differentiation.

## Risks / Trade-offs

- **10-second hang if Shizuku becomes unavailable mid-bind** → Mitigated by `binderDeadListener`, which transitions `_state` to `NotRunning` if Shizuku dies. This unblocks the `first { }` collector immediately since `State.NotRunning` fails the predicate — the timeout path is hit only in truly degenerate cases.

- **`TimeoutCancellationException` caught by the generic catch** → This is intentional and safe. The outer coroutine scope in `TemporaryAllowReceiver` is `Dispatchers.IO` with no structured cancellation relationship to the timeout, so the timeout only cancels the inner `withTimeout` block, not the receiver's coroutine.

- **`State.Ready` never emitted if `binderReceivedListenerSticky` fires before the wait starts** → Not a risk. `addBinderReceivedListenerSticky` replays the event to new listeners if the binder is already available. If `State.Ready` is set before `first { }` begins collecting, `StateFlow` delivers the current value immediately to the collector — there is no missed-event window.

## Migration Plan

No data migration or deployment steps required. The change is entirely in-process logic. The app can be updated and deployed normally. No rollback procedure needed beyond reverting the APK.

## Open Questions

_None._
