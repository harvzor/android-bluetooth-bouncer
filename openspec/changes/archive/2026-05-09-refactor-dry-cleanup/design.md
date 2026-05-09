## Context

The Bluetooth Bouncer app (~2,700 lines, 19 Kotlin files) has accumulated copy-paste debt across several layers as features were added incrementally. The most concentrated duplication sits in the Shizuku layer (`BluetoothBouncerUserService`, `ShizukuHelper`), where three AIDL operations have grown structurally identical but are maintained as three separate bodies of code. Secondary duplication appears in the ViewModel (section-classification logic), across all four `BroadcastReceiver` classes (async lifecycle boilerplate), between two services (a hidden-API reflection hack), and in two UI screens (raw colour literals).

There are no external dependencies to change, no database migrations, and no API surface changes. All refactors are behaviour-preserving restructuring of existing code.

## Goals / Non-Goals

**Goals:**
- Eliminate all identified code duplication (6 instances)
- Introduce semantic naming for repeated magic values (colours)
- Make each responsibility sit in exactly one place
- Reduce total line count by ~350 lines with no behaviour change
- Keep every refactor independently reviewable and reversible

**Non-Goals:**
- No dependency injection framework introduction (Hilt/Koin)
- No ViewModel decomposition beyond the targeted deduplication
- No string resource extraction or localisation changes
- No test additions (the app currently has no tests to protect)
- No feature additions or behaviour changes of any kind

## Decisions

### D1 — `forEachProfile()` helper in `BluetoothBouncerUserService`

**Decision**: Extract a private `forEachProfile(macAddress: String, action: (proxy: Any, device: BluetoothDevice) -> Int): IntArray` method.

**Rationale**: The three AIDL overrides differ only in the per-proxy call — the outer structure (reinit check, MAC→device resolution, three proxy futures with 8-second timeouts, IntArray construction, logging) is identical. The lambda captures the per-proxy logic cleanly without losing any of the existing error handling.

**Alternatives considered**:
- Enum/sealed class per operation → adds indirection with no benefit over a direct lambda.
- Inline higher-order function → no meaningful difference at the call site.

**Signature**:
```kotlin
private fun forEachProfile(
    macAddress: String,
    action: (proxy: Any, device: BluetoothDevice) -> Int,
): IntArray
```

The `PROXY_TIMEOUT_SEC = 8L` constant is extracted from the repeated inline literal.

---

### D2 — `callService()` helper in `ShizukuHelper`

**Decision**: Extract a private `suspend fun callService(operationName: String, call: (IBluetoothBouncerUserService) -> IntArray): Result<IntArray>`.

**Rationale**: `awaitServiceIfNeeded()` + null check + `results.none { it == 1 }` check + exception wrapping is identical in all three public methods. `operationName` is used only in the error message, which is all that differed between them (plus the specific check message in `setConnectionPolicy` — this is dropped in favour of the generic message that applies equally to all three operations).

**Alternatives considered**:
- Sealed class for operation types → unnecessary; the lambda already parameterises the call cleanly.

---

### D3 — `BluetoothAclHelper` utility object

**Decision**: Create `util/BluetoothAclHelper.kt` as a top-level `object` with two functions:
- `isConnected(device: BluetoothDevice): Boolean` — wraps the hidden-API reflection call
- `getConnectedAddresses(adapter: BluetoothAdapter): Set<String>` — maps over bonded devices

**Rationale**: The same reflection pattern (`getDeclaredMethod("isConnected").invoke(device)`) exists in both `DeviceListViewModel.getAclConnectedAddresses()` and `DeviceWatcherService.isDeviceAclConnected()`. A shared object removes the duplication and puts the `@SuppressLint("MissingPermission")` annotation in one place. The `object` (rather than a `companion object` on one of the callers) makes the utility equally accessible from both without creating a dependency direction between them.

**Location**: `app/src/main/java/net/harveywilliams/bluetoothbouncer/util/BluetoothAclHelper.kt`

---

### D4 — `computeSection()` in `DeviceListViewModel`

**Decision**: Extract a private `fun computeSection(isConnected: Boolean, isDetected: Boolean, isBlocked: Boolean, hasRecentDetection: Boolean): DeviceSection` function.

**Rationale**: The `when` expression for mapping device state to `DeviceSection` is duplicated in `refreshDeviceList()` (initial construction) and the decay ticker's `_uiState.update` lambda (remapping during decay). Extracting it ensures both paths always agree on classification logic.

**Approach**: The decay ticker currently reads `device.isBlocked` from the existing `DeviceUiModel` — that field is already present, so the helper function needs no additional state. The function is `private` and `internal` to the ViewModel.

---

### D5 — `launchAsync` extension on `BroadcastReceiver`

**Decision**: Create `receivers/ReceiverExtensions.kt` with:
```kotlin
fun BroadcastReceiver.launchAsync(
    context: Context,
    block: suspend (app: BluetoothBouncerApp) -> Unit,
)
```

**Rationale**: All four receivers repeat `goAsync()` + `CoroutineScope(Dispatchers.IO).launch { try { block(app) } finally { pendingResult.finish() } }`. The extension naturally places the helper in the `receivers` package where all callers live, and the `(app: BluetoothBouncerApp)` parameter avoids an additional cast inside each lambda.

**Note on scope**: The `CoroutineScope(Dispatchers.IO)` is intentionally not cancelled — this is intentional. The receiver's `goAsync()` bound provides the effective lifecycle limit. This is the existing pattern; the helper does not change the cancellation semantics.

---

### D6 — `AppColors` semantic constants

**Decision**: Create `ui/theme/AppColors.kt` as a top-level `object`:
```kotlin
object AppColors {
    val ShizukuReady = Color(0xFF4CAF50)
    val DeviceConnected = Color(0xFF87CEEB)
    val DeviceDetected = Color(0xFFE8A06C)
}
```

**Rationale**: Raw hex colour literals are scattered across `DeviceListScreen` (4 usages) and `ShizukuSetupScreen` (1 usage). Named constants make the intent clear and ensure both screens stay in sync if the palette changes.

**Location**: `ui/theme/` alongside `Theme.kt` — consistent with where colour definitions naturally live in the Compose Material3 pattern. Not added to the theme's `ColorScheme` because these are semantic overrides (Bluetooth-domain meanings), not Material3 role overrides.

---

### D7 — `NearbyDeviceTracker` extraction from `BluetoothBouncerApp`

**Decision**: Create `service/NearbyDeviceTracker.kt`:
```kotlin
class NearbyDeviceTracker(private val applicationScope: CoroutineScope) {
    val nearbyDevices: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    private val pendingRemovals: MutableMap<String, Job> = mutableMapOf()

    fun addDevice(mac: String)
    fun scheduleRemoval(mac: String, deviceName: String)
    fun cancelPendingRemoval(mac: String)
}
```

**Rationale**: `BluetoothBouncerApp` currently owns `nearbyDevices`, `pendingRemovals`, `scheduleNearbyRemoval()`, and `cancelPendingRemoval()` — state and behaviour that belong to "tracking which devices are nearby" rather than to Application lifecycle management. Extracting them gives the tracker a clear single responsibility and makes it independently readable.

The notification observer (`launchNotificationObserver()`) and CDM cleanup (`cleanUpStaleCdmAssociations()`) remain in `BluetoothBouncerApp` — they coordinate multiple concerns (Room, notifications) and are legitimately Application-level orchestration.

`BluetoothBouncerApp` exposes: `val nearbyTracker: NearbyDeviceTracker by lazy { NearbyDeviceTracker(applicationScope) }`. Callers migrate from `app.nearbyDevices` → `app.nearbyTracker.nearbyDevices` and from `app.scheduleNearbyRemoval()` / `app.cancelPendingRemoval()` → `app.nearbyTracker.*`.

**Location**: `service/` — the tracker is consumed by `DeviceWatcherService` and supports the CDM service layer.

## Risks / Trade-offs

- **[Risk] Behaviour regression from lambda extraction (D1, D2)** → Each extracted helper is a direct mechanical consolidation of existing identical code. The diff for each method will be near-empty once the outer structure is replaced. Mitigation: test manually on device after each step.

- **[Risk] `forEachProfile` changes error logging granularity (D1)** → Currently each proxy has its own `Log.w(TAG, "A2DP proxy timeout/error", e)` message. The extracted helper will use a `profiles` list with names, preserving per-proxy identification in log output.

- **[Risk] `callService` drops the Shizuku-specific hint in `setConnectionPolicy` error message (D2)** → The original message included "Check that Shizuku is running and has BLUETOOTH_PRIVILEGED." The generic message omits this hint. Mitigation: acceptable — the `ShizukuHelper.State` flow already surfaces "not ready" to the UI; the error message is developer-facing only.

- **[Risk] `launchAsync` loses explicit `catch` in some receivers (D5)** → `TemporaryAllowReceiver` and `DisconnectReceiver` have per-call `catch` blocks inside the lambda. The helper's `try/finally` does not catch — each lambda retains its own `try/catch`. The helper only handles `pendingResult.finish()`.

- **[Risk] Call-site migration for `NearbyDeviceTracker` (D7)** → `DeviceWatcherService` and `BluetoothBouncerApp.launchNotificationObserver()` both reference `nearbyDevices` directly. Migration is straightforward (`app.nearbyDevices` → `app.nearbyTracker.nearbyDevices`) but must be complete — a partial migration would cause a compile error.
