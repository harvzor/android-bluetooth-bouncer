## Why

Every code change requires manual testing on a physical Xiaomi device — pairing devices, tapping through flows, verifying Bluetooth state — with no way to catch regressions automatically. A structured agentic testing approach (AI agent driving the real app via ADB on a real phone) replaces this manual loop with a repeatable, automated test run.

## What Changes

- New `TESTING.md` in the repo documenting the full test plan, phases, checkpoints, and pass/fail criteria for the agent to follow
- New `android-adb-testing` skill (`~/.agents/skills/android-adb-testing/SKILL.md`) teaching the agent how to drive any Android app via ADB (UI dumps, tapping, screenshotting, asserting state)
- `.adb-test-tmp/` added to `.gitignore` so temporary ADB test artifacts (screenshots, UI dumps) are never committed
- `TESTING_WITH_BUMBLE.md` already exists — no changes needed

## Capabilities

### New Capabilities

- `agentic-test-runner`: The agent can execute a structured, phased E2E test suite against the real app on a physical device via ADB — covering first-launch flow, Shizuku setup, device list smoke tests, block/unblock, persistence, and connect/disconnect on blocked devices

### Modified Capabilities

## Impact

- No production code changes
- New dev tooling only: `TESTING.md`, skill file, `.gitignore` update
- Requires: physical Android device with ADB, Shizuku installed and running, a paired Bluetooth device available for block/unblock testing
- The `ANDROID_HOME` and ADB path requirements already documented in `AGENTS.md` apply
