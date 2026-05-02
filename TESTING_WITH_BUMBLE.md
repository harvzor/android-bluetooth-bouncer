# Testing with Bumble

## The Idea

[Bumble](https://google.github.io/bumble/) is a full Bluetooth stack written in Python. It can impersonate any Bluetooth device — HID keyboard, mouse, headphones, etc. — connecting at the HCI layer so the other side sees it as real hardware.

The goal: use Bumble to automate the "pair a device, trigger a block, verify it disconnects" loop that currently requires manually turning on a physical Bluetooth device.

## Architecture

```
Your PC                            Your Phone (USB + BT)
┌──────────────────┐               ┌───────────────────────┐
│ Bumble (Python)  │  real BT RF   │ Bluetooth Bouncer     │
│ simulating HID   │◄─────────────►│ running with Shizuku  │
│ keyboard/mouse   │               └───────────────────────┘
└──────────────────┘                         │
         │                                   │
┌──────────────────┐          ADB            │
│ Test script      │◄───────────────────────►│
│ (Python)         │  reads state, verifies  │
└──────────────────┘
```

## What a Test Would Look Like

1. Start Bumble simulating an HID device (e.g. keyboard)
2. Pair it with the phone programmatically
3. Verify the app detects the new bonded device
4. Trigger the block action (via ADB tap or UI automation)
5. Verify Bumble gets disconnected / can no longer reconnect
6. Unblock, verify reconnection succeeds
7. Unpair and clean up

This turns a 5-minute manual test into a 30-second automated one. Still requires the phone connected via USB, but removes the need to own/find a dedicated Bluetooth test device.

## Why This Is Better Than Emulator Approaches

The emulator's `BluetoothAdapter` returns `null` — no amount of Netsim/gRPC work gets past this for app-level testing. Bumble on a physical device uses real RF, real Android Bluetooth APIs, real Shizuku — it tests the actual production path.

## Prerequisites

- PC with a Bluetooth adapter (or USB BT dongle)
- Python 3.10+
- `pip install bumble`
- Phone connected via USB with ADB authorized
- Shizuku running on the phone

## Current Status

**Not yet implemented.** This is a future idea to explore once the current ADB-based agentic testing approach is established.

## References

- Bumble docs: https://google.github.io/bumble/
- Bumble HID profile: https://google.github.io/bumble/platforms/android.html
- Netsim (emulator virtual BT controller — not useful for this): https://android.googlesource.com/platform/tools/netsim
