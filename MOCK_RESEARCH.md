# Bluetooth Mock/Emulator Testing Research

## Can We Mock Bluetooth in the Android Emulator?

**No — there is no practical way to mock full Bluetooth connections (especially HID_HOST + `setConnectionPolicy`) in the emulator.** The emulator's `BluetoothAdapter` returns `null`. This has not changed even in Android 16.

---

## Options Ranked by Practicality

### 1. Physical Device (Required)

The only viable path for end-to-end testing of the Shizuku + hidden API + real Bluetooth chain. Nothing else can replace this.

### 2. Robolectric + Mockito (Unit Tests)

The most practical option for automated testing of app logic:

- Robolectric's `ShadowBluetoothAdapter` has `setProfileProxy(int, BluetoothProfile)` — you can pass `4` (HID_HOST) with a Mockito mock.
- When the app calls `getProfileProxy()`, it fires `onServiceConnected` with the mock.
- You can verify `setConnectionPolicy` calls and test the `IntArray` result-parsing logic in `ShizukuHelper`.
- **Limitation**: Only covers up to the Shizuku boundary — cannot test the actual reflection/IPC.

### 3. Bumble + Netsim (Experimental)

Google's [Bumble](https://google.github.io/bumble/) Python stack can connect to the emulator's virtual Bluetooth controller (Netsim) via gRPC. In theory, you could simulate a remote HID device. In practice:

- Operates at the HCI layer, far below framework APIs.
- HID_HOST profile support in the emulator is unproven.
- Complex setup, not designed for app-level testing.
- Best suited for BLE/GATT testing, not Classic Bluetooth profiles.

### 4. Emulator Native Bluetooth

Does not exist. `getDefaultAdapter()` returns `null` in the emulator.

---

## Background Detail

### Emulator Virtual Bluetooth Controller

The emulator ships with a virtual Bluetooth controller (originally **Root Canal**, now replaced by **Netsim**). This controller operates at the HCI (Host Controller Interface) level and is used internally by Google for Bluetooth stack development. It is **not** surfaced to app developers through normal Android APIs.

Netsim exposes a gRPC interface and allows multiple virtual Android devices (or Bumble) to connect to the same virtual radio environment. However, profile-level behavior (HID_HOST, A2DP, etc.) in the emulator is largely untested and unsupported for app developers.

### Robolectric Shadow API Detail

`ShadowBluetoothAdapter` provides:

| Method | Purpose |
|--------|---------|
| `setProfileProxy(int profile, BluetoothProfile proxy)` | Sets a mock proxy for any profile ID (including `4` for HID_HOST). `getProfileProxy()` will immediately invoke `onServiceConnected` with the mock. |
| `setProfileConnectionState(int profile, int state)` | Sets the connection state for any profile. |
| `getProfileConnectionState(int profile)` | Returns the state you set. |
| `setBondedDevices(Set<BluetoothDevice>)` | Sets the bonded device list. |

### Bumble Capabilities

- Full Bluetooth stack written in Python.
- Has an `android-netsim` transport that connects to the emulator's Netsim via gRPC.
- Supports HID profile, A2DP, HFP, SDP, RFComm, GATT, and more.
- Can act as a simulated remote Bluetooth device.
- Has an Android Remote HCI app (requires root + SELinux disabled — not practical for CI).

---

## Summary

| Approach | Viability for HID_HOST + setConnectionPolicy | Maturity |
|----------|-----------------------------------------------|----------|
| **Physical device** | **Required** for full integration testing | Production |
| **Robolectric shadows** | Good for unit-testing UI/ViewModel/ShizukuHelper logic | Mature |
| **Mockito test doubles** | Good for testing result handling (IntArray parsing) | Mature |
| **Bumble + Netsim** | Theoretically possible, extremely complex, HID_HOST unproven | Experimental |
| **Emulator native BT** | Not available | N/A |

**Bottom line**: The physical device remains mandatory for integration testing. For automated unit tests, Robolectric's `ShadowBluetoothAdapter.setProfileProxy()` + Mockito is the practical path — it covers everything up to the Shizuku boundary without needing Bluetooth hardware.
