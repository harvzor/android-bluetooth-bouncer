## Context

`DeviceListViewModel.refreshDeviceList()` classifies devices into sections (Connected / Detected / Blocked / Allowed) by combining two sources of truth:

1. **Profile proxies** (`a2dpProxy`, `headsetProxy`) — the authoritative source for profile-level connection state. Obtained asynchronously via `adapter.getProfileProxy()`.
2. **ACL state** (`getAclConnectedAddresses()`) — per-device `isConnected()` reflection. Synchronous and always available.

The connected address set is computed as:
```
connectedAddresses = profileConnected + (aclConnected − blockedAddresses)
```

The subtraction of `blockedAddresses` is intentional: the Bluetooth stack can maintain an ACL link for a blocked device as part of policy enforcement, so including ACL state for blocked devices would generate false "Connected" badges.

On cold start triggered by a notification action, the profile proxies are `null` at the time of the first `refreshDeviceList()` call because `getProfileProxy()` is asynchronous. Additionally, since `onServiceConnected` callbacks never emit into `refreshSignal`, the stale state persists until an unrelated broadcast event arrives.

For temporarily-allowed devices, the ACL exclusion compounds the proxy race: even if the device is ACL-connected and the connection is legitimate, the device is still in `blockedAddresses`, so it falls through to the Detected section.

## Goals / Non-Goals

**Goals:**
- Device correctly shows as "Temporarily connected" immediately when the app is opened after a cold-start notification action
- State converges to profile-level accuracy once proxies are available, without requiring user interaction
- No regression for normal blocked devices (false-positive Connected badge must not reappear)

**Non-Goals:**
- Fixing any other cold-start race (Shizuku binder timing, Room emission ordering, etc.)
- Changing UI chrome or section structure
- Adding persistence or recovery logic for proxy state across process restarts

## Decisions

### Decision 1: Emit `refreshSignal` from `onServiceConnected`

**Chosen:** Add `refreshSignal.tryEmit(Unit)` at the end of each `onServiceConnected` callback inside `initProfileProxies()`.

**Rationale:** When the proxy callbacks fire, the proxy references are now non-null, so a subsequent `refreshDeviceList()` will produce accurate profile-level results. The existing 300 ms debounce on `refreshSignal` coalesces the A2DP and Headset callbacks when they arrive close together, so at most one extra refresh is triggered.

**Alternatives considered:**
- *Polling loop* — poll proxy state until non-null. Introduces unnecessary complexity and battery cost with no benefit over an event-driven approach.
- *Delay / postDelayed* — wait a fixed time before first refresh. Fragile; different devices have different proxy bind latencies.

---

### Decision 2: Exclude temporarily-allowed devices from the ACL blocked filter

**Chosen:** Change the `connectedAddresses` computation to:
```
connectedAddresses = profileConnected + (aclConnected − (blockedAddresses − temporarilyAllowedAddresses))
```

**Rationale:** The original filter exists to prevent false-positive Connected badges for devices where the OS maintains an ACL link *against the user's intent* (i.e., for policy enforcement on a blocked device). A temporarily-allowed device is connected *with* the user's intent — the user just tapped "Allow temporarily". Excluding it from the filter is both semantically correct and provides the correct fallback state while profile proxies are loading.

**Alternatives considered:**
- *Special-case the section assignment for temporarily-allowed devices* — treat `isTemporarilyAllowed && isDetected` as Connected-section. This would work for the section but `isConnected` would still be `false`, causing the "Temporarily connected" label (which checks `isConnected && isTemporarilyAllowed`) not to appear. Would require threading the logic through two separate places.
- *Only apply the fix when proxies are null* — conditionally restore the device to connected state only when proxies haven't loaded yet. More complex, and incorrect: the device *is* connected regardless of proxy state.

## Risks / Trade-offs

**[Risk] The filter change could introduce false-positive Connected badges for temporarily-allowed devices that are ACL-present but not actually profile-connected.**

Mitigation: A temporarily-allowed device that has an ACL link but no profiles will still show correctly as "Detected" — the `detectedAddresses` set is `aclConnected − connectedAddresses`, so if the device genuinely hasn't established a profile connection, `connectedAddresses` still won't include it via `profileConnected`, and the ACL fallback only promotes it to Connected when it really is ACL-up. This is the correct transient state (policy set to ALLOWED, OS is in the process of connecting). Net result: it may briefly show Connected via ACL before profiles are up, which is no worse than the "Temporarily connected" label that appears once profiles connect — the user just sees Connected slightly earlier, which is accurate.

**[Risk] Two `refreshSignal` emissions per proxy init (one for A2DP, one for Headset) could cause visible flicker.**

Mitigation: The 300 ms debounce on `refreshSignal` naturally absorbs both callbacks if they fire within that window, which they always do in practice (both proxies bind in the same Bluetooth system service call).

## Open Questions

_(none — changes are self-contained and low-risk)_
