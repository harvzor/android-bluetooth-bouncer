## Context

Manual testing is the current only way to verify Bluetooth Bouncer. Every change requires the developer to physically pair a device, tap through the app, and observe Bluetooth state. This is slow, error-prone, and provides no regression safety net.

The solution is agentic testing: an AI agent drives the real app on a real phone via ADB shell commands, following a structured test plan. This was validated in a live session — the agent successfully unblocked and re-blocked a paired Bluetooth device by reading the UI hierarchy, computing tap coordinates, executing taps, and asserting the result via screenshots and re-dumps.

Two deliverables make this work:
1. A **skill** (`android-adb-testing`) that teaches the agent the generic methodology for ADB-driven UI testing on Android
2. A **project-specific test plan** (`TESTING.md`) that defines what to test in Bluetooth Bouncer

## Goals / Non-Goals

**Goals:**
- Document a repeatable, phased E2E test run the agent can follow from a single instruction
- Teach the agent how to find elements in the UI hierarchy, compute tap coordinates, take screenshots, and assert state — without any test framework or instrumentation
- Cover the full critical path: fresh install → permissions → Shizuku setup → device list → block/unblock → persistence → connect/disconnect on blocked device
- Keep temporary ADB artifacts out of git

**Non-Goals:**
- Automated CI/CD (tests require a physical device with Shizuku and a live Bluetooth device)
- Mocked or emulator-based testing (established as not viable for this app's Bluetooth stack)
- Bumble-based automated Bluetooth device simulation (future, documented separately in `TESTING_WITH_BUMBLE.md`)
- Adding `testTag` modifiers or any other production code changes

## Decisions

### Decision 1: ADB shell commands over Compose UI Test framework

**Chosen:** Raw ADB (`uiautomator dump`, `input tap`, `screencap`) driven by the agent

**Alternatives considered:**
- `connectedAndroidTest` with `createAndroidComposeRule` — requires writing and maintaining Kotlin test code, a build step per run, and test infrastructure setup. Adds ongoing maintenance burden.
- Appium / UiAutomator2 server — adds a Python/Node dependency, server lifecycle management, and more setup than the value warrants for a solo project.

**Rationale:** The agent can read the UI hierarchy XML, compute coordinates, and tap directly. No framework, no compilation, no test runner. The only prerequisite is ADB. Validated working in a live session.

### Decision 2: Skill + project doc split

**Chosen:** Generic `android-adb-testing` skill + app-specific `TESTING.md`

**Rationale:** The methodology for ADB-driven testing (how to dump UI, parse bounds, tap, assert) is reusable across any Android project. The test scenarios (which phases, what devices, what assertions) are specific to Bluetooth Bouncer. Keeping them separate means the skill can be reused on other Android projects without carrying Bluetooth Bouncer specifics.

### Decision 3: Temp files go in `.adb-test-tmp/`

**Chosen:** All screenshots and UI XML dumps written to `.adb-test-tmp/` (gitignored), never to the repo root.

**Rationale:** Discovered during the live validation session — the agent left `ui.xml`, `ui2.xml`, `screen.png` etc. in the repo root. A dedicated ignored directory prevents this.

### Decision 4: Full test vs. quick mode

**Chosen:** Agent asks at the start whether to run full (Phase 0–7, with reinstall) or quick (Phase 3–7, skip reinstall).

**Rationale:** Full mode tests the first-launch flow (permissions, Shizuku setup) and is the right default for "did I break anything?" checks before a release. Quick mode is for rapid regression checks during active development where reinstalling every time is unnecessary overhead.

### Decision 5: Device auto-detection

**Chosen:** Agent auto-selects the physical device (non-emulator) from `adb devices`. Falls back to asking the user if ambiguous.

**Rationale:** The emulator is always present in this dev environment (`emulator-5554`). Requiring the user to specify `-s ea181dcd` every time is friction. Auto-detection eliminates it for the common case.

### Decision 6: User checkpoint before Bluetooth-dependent phases

**Chosen:** Agent pauses before Phase 4 and asks the user to confirm the target Bluetooth device is turned on and connected.

**Rationale:** The block/unblock tests require the device to auto-connect when unblocked. If the device is off, the test will hang waiting for a CONNECTED state that never arrives. A checkpoint is more reliable than a timeout.

## Risks / Trade-offs

- **UI hierarchy has no testTag modifiers** → Element finding relies on text content and positional relationships. If button labels change (e.g. "Allowed" → "Allow"), selectors break. Mitigation: document the exact strings used in `TESTING.md` and update them alongside UI changes.

- **CDM system dialog in Phase 6** → The CompanionDeviceManager association dialog is system UI (different package). It should be findable via `uiautomator dump` but has not been tested yet. Mitigation: note it as a known risk in `TESTING.md`; if it can't be automated, Phase 6 step 6.2 becomes a manual tap with a user checkpoint.

- **Bluetooth reconnection timing** → After unblocking, the device reconnects asynchronously. Tests that assert CONNECTED state after an unblock must wait. Mitigation: skill documents a polling approach (re-dump UI every second, up to a configurable timeout).

- **Physical device dependency** → Tests cannot run without the phone connected via USB and Shizuku running. Mitigation: by design, documented clearly in prerequisites.

## Open Questions

- Can the CDM association dialog (Phase 6, step 6.2) be reliably found and tapped via `uiautomator dump`? To be determined when Phase 6 is first executed.
